const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

// We need to fetch product consumption days for retention calculation
async function getProductConsumptionDays(orgId, sku) {
    try {
        const prodDoc = await db.collection("organizations").doc(orgId).collection("products").doc(sku).get();
        if (prodDoc.exists) {
            const data = prodDoc.data();
            if (data.consumptionDays) {
                return Number(data.consumptionDays);
            }
        }
    } catch (e) {
        console.error("Error fetching product:", e);
    }
    return 30; // Default 30 days
}

exports.onLeadWritten = functions.region('asia-south1').firestore
    .document("organizations/{orgId}/leads/{leadId}")
    .onWrite(async (change, context) => {
        const { orgId, leadId } = context.params;

        // Document was deleted
        if (!change.after.exists) {
            return null;
        }

        const dataBefore = change.before.exists ? change.before.data() : {};
        const dataAfter = change.after.data();

        // 1. Prevent Infinite Loops
        // Only proceed if status actually changed, it's a new document, OR assignedToName is missing
        const statusChanged = dataBefore.status !== dataAfter.status;
        const isNew = !change.before.exists;
        const needsNameDenormalization = dataAfter.assignedTo && !dataAfter.assignedToName;

        // If status didn't change, it's not a new document, and we don't need to denormalize name
        if (!statusChanged && !isNew && !needsNameDenormalization) {
            return null;
        }

        const status = dataAfter.status || "";
        let updates = {};

        // 0. Denormalize Telecaller Name (Silicon Valley Architecture)
        // If assignedTo exists but assignedToName is missing, fetch and save it permanently
        if (dataAfter.assignedTo && !dataAfter.assignedToName) {
            try {
                const userDoc = await db.collection("organizations").doc(orgId).collection("users").doc(dataAfter.assignedTo).get();
                if (userDoc.exists) {
                    updates.assignedToName = userDoc.data().name || "Unknown Agent";
                } else {
                    updates.assignedToName = "Deleted/Unknown Agent";
                }
            } catch (e) {
                console.error("Error fetching user name:", e);
                updates.assignedToName = "Error Fetching Name";
            }
        }

        // 2. Business Rule: Order Placed -> Pending Pack
        if (status === "Order Placed") {
            // Only update if dispatchStatus isn't already set to Pending
            if (dataAfter.dispatchStatus !== "Pending") {
                updates.dispatchStatus = "Pending";
                updates.orderDate = admin.firestore.FieldValue.serverTimestamp();
            }
        } 
        // 3. Business Rule: Delivered -> Calculate Exhaustion Date
        else if (status === "Delivered") {
            if (dataAfter.dispatchStatus !== "Delivered") {
                updates.dispatchStatus = "Delivered";
                updates.deliveredAt = new Date().toISOString();
                
                // Calculate exhaustionDate
                let shortestConsumptionDays = 30;
                if (dataAfter.baseProductsBreakdown) {
                    const parts = dataAfter.baseProductsBreakdown.split(',').filter(Boolean);
                    let minDays = Infinity;
                    for (const part of parts) {
                        const [sku, qty] = part.split(':');
                        const pDays = await getProductConsumptionDays(orgId, sku);
                        const q = Number(qty) || 1;
                        const totalDays = pDays * q;
                        if (totalDays < minDays) minDays = totalDays;
                    }
                    if (minDays !== Infinity) shortestConsumptionDays = minDays;
                }

                const today = new Date();
                today.setDate(today.getDate() + shortestConsumptionDays);
                updates.exhaustionDate = today.toISOString();
            }
        }
        // 4. Business Rule: RTO / Returned
        else if (status === "RTO" || status === "Returned") {
            if (dataAfter.dispatchStatus !== "Returned") {
                updates.dispatchStatus = "Returned";
                updates.returnedAt = new Date().toISOString();
            }
        }
        // 5. Business Rule: Reverse / Mistake handling
        // If it was Order Placed, but now it's Cancelled or Not Interested
        else if (dataBefore.status === "Order Placed" && (status === "Order Cancelled" || status === "Not Interested")) {
            updates.dispatchStatus = "Cancelled";
        }
        else if (status === "Order Cancelled") {
            if (dataAfter.dispatchStatus !== "Cancelled") {
                updates.dispatchStatus = "Cancelled";
            }
        }

        // If there are updates to apply, apply them to break the loop
        if (Object.keys(updates).length > 0) {
            console.log(`Applying Cloud Function Rules for Lead ${leadId} in Org ${orgId}`);
            return change.after.ref.update(updates);
        }

        return null;
    });

exports.generateWhatsAppTemplate = functions.region('asia-south1')
    .runWith({ secrets: ["GEMINI_API_KEY"] })
    .https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError(
            'unauthenticated', 
            'You must be logged in to generate templates.'
        );
    }

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
        throw new functions.https.HttpsError(
            'failed-precondition', 
            'GEMINI_API_KEY environment variable is missing.'
        );
    }

    const { status, language, orgName, products } = data;
    
    if (!status || !language) {
        throw new functions.https.HttpsError(
            'invalid-argument', 
            'Status and Language are required fields.'
        );
    }

    const allowedTags = [
        '{{customer_name}}', '{{delivery_address}}', '{{product_list}}', '{{product_list_with_quantity}}',
        '{{regular_price}}', '{{special_price}}', '{{saved_amount}}', '{{discount_percentage}}',
        '{{payment_status}}', '{{upi_payment_link}}', '{{org_name}}', '{{support_number}}'
    ];

    const prompt = `You are an expert Silicon Valley level copywriter for high-converting sales WhatsApp messages.
Write a highly converting, professional, and concise WhatsApp message for a customer whose lead status is '${status}'.
The message MUST be in ${language}.
The organization sending the message is '${orgName || 'our company'}'.
Their available products might be related to: '${products || 'our catalog'}'.

CRITICAL RULES:
1. You MUST use placeholder tags from this EXACT list where appropriate: ${allowedTags.join(', ')}.
2. NEVER invent custom tags. Only use the ones provided in the list above.
3. If the status is "Order Placed", you MUST prioritize using pricing tags (like {{regular_price}}, {{special_price}}, {{discount_percentage}}) and payment tags ({{payment_status}}, {{upi_payment_link}}) to create a comprehensive order summary. Make the tone celebratory and reassuring.
4. If the status is NOT related to a placed order (e.g., "Follow-up", "Product Enquiry"), DO NOT spam payment links or saved amounts unless it fits perfectly into a soft sales pitch.
5. Do NOT write any conversational filler (e.g. "Here is your template", "Sure!"). 
6. Output ONLY the raw template text that will be directly sent to the customer.
7. Use polite emojis naturally.`;

    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: prompt }] }],
                generationConfig: { temperature: 0.3 }
            })
        });

        if (!response.ok) {
            const err = await response.text();
            console.error("Gemini API Error:", err);
            if (response.status === 429) {
                throw new functions.https.HttpsError('resource-exhausted', 'AI is currently busy due to high demand. Please try again in a few moments.');
            }
            throw new functions.https.HttpsError('internal', 'AI generation failed from Gemini API.');
        }

        const json = await response.json();
        const generatedText = json.candidates?.[0]?.content?.parts?.[0]?.text || '';
        
        return { text: generatedText.trim() };
    } catch (e) {
        console.error("Error calling Gemini API:", e);
        throw new functions.https.HttpsError('internal', 'An error occurred during AI generation.');
    }
});
