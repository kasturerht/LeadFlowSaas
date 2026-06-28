package com.nexaleads.app.utils

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactUtils {
    
    /**
     * Silently saves a new contact to the device's default phonebook.
     * Returns true if successful, false otherwise.
     */
    suspend fun saveContactSilently(context: Context, name: String, phone: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val ops = ArrayList<ContentProviderOperation>()

                // 1. Insert an empty RawContact bound to the local device account
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                // 2. Insert the Contact Name
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            name
                        )
                        .build()
                )

                // 3. Insert the Phone Number
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            phone
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )

                // Execute the batch transaction
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
