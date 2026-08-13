import json
import collections
import sys

print("Loading data...")
with open('leads_export.json', 'r', encoding='utf-8') as f:
    leads = json.load(f)

with open('interactions_export.json', 'r', encoding='utf-8') as f:
    interactions = json.load(f)

print(f"Total Leads: {len(leads)}")
print(f"Total Interactions: {len(interactions)}")

print("\n--- LEADS ANALYSIS ---")
status_counts = collections.Counter()
substatus_counts = collections.Counter()
source_counts = collections.Counter()
missing_fields = collections.Counter()

for lead in leads:
    status_counts[lead.get('status')] += 1
    substatus_counts[lead.get('subStatus')] += 1
    source_counts[lead.get('source')] += 1
    
    # Check anomalies
    if not lead.get('name'):
        missing_fields['name'] += 1
    if not lead.get('phone'):
        missing_fields['phone'] += 1
    if not lead.get('assignedTo'):
        missing_fields['assignedTo'] += 1
    
    # Check for empty string vs null vs undefined
    for k, v in lead.items():
        if v == "":
            missing_fields[f"{k} (Empty String)"] += 1
        elif v is None:
            missing_fields[f"{k} (Null)"] += 1

print(f"\nTop 5 Statuses: {status_counts.most_common(5)}")
print(f"Top 5 SubStatuses: {substatus_counts.most_common(5)}")
print(f"Top 5 Sources: {source_counts.most_common(5)}")
print(f"\nMissing/Empty Fields (Top 10): {missing_fields.most_common(10)}")

print("\n--- INTERACTIONS ANALYSIS ---")
interaction_types = collections.Counter()
missing_lead_ref = collections.Counter()

lead_ids = set([l['id'] for l in leads])
orphan_interactions = 0

for interaction in interactions:
    interaction_types[interaction.get('statusAfter')] += 1
    
    if interaction.get('leadId') not in lead_ids:
        orphan_interactions += 1

print(f"Top 5 Interaction Statuses: {interaction_types.most_common(5)}")
print(f"Orphan Interactions (Lead ID missing/deleted): {orphan_interactions}")

print("\n--- STRUCTURAL RECOMMENDATIONS (100k SCALE) ---")
# Calculate average size
leads_size_mb = sys.getsizeof(str(leads)) / (1024 * 1024)
print(f"Current leads dataset memory size (approx JSON string): {leads_size_mb:.2f} MB for {len(leads)} leads")
print(f"Projected memory for 100k leads: {(leads_size_mb / max(1, len(leads))) * 100000:.2f} MB")
