package com.example.jarvis

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object JarvisContactsManager {

    data class ContactInfo(
        val name: String,
        val number: String
    )

    // CHECK CONTACT PERMISSION

    fun hasPermission(context: Context): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // SEARCH SAVED CONTACTS

    fun searchContact(
        context: Context,
        contactName: String
    ): List<ContactInfo> {

        if (!hasPermission(context)) {
            return emptyList()
        }

        if (contactName.isBlank()) {
            return emptyList()
        }

        val contacts = mutableListOf<ContactInfo>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"

        val selectionArgs = arrayOf(
            "%${contactName.trim()}%"
        )

        try {

            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->

                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                if (nameIndex < 0 || numberIndex < 0) {
                    return@use
                }

                while (cursor.moveToNext()) {

                    val name = cursor.getString(nameIndex)
                        ?: continue

                    val number = cursor.getString(numberIndex)
                        ?: continue

                    contacts.add(
                        ContactInfo(name, number)
                    )
                }
            }

        } catch (e: SecurityException) {
            return emptyList()
        }

        return contacts.distinctBy {
            it.name to it.number
        }
    }

    // OPEN CALL CONFIRMATION

    fun confirmCall(
        context: Context,
        contact: ContactInfo
    ) {

        val activity = context as? android.app.Activity

        if (activity == null) {
            openDialer(context, contact.number)
            return
        }

        activity.runOnUiThread {

            AlertDialog.Builder(activity)
                .setTitle("JARVIS Voice Calling")
                .setMessage(
                    "क्या आप कॉल करना चाहते हैं?\n\n" +
                    "नाम: ${contact.name}\n" +
                    "नंबर: ${contact.number}"
                )
                .setPositiveButton("Call") { _, _ ->

                    openDialer(
                        activity,
                        contact.number
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // OPEN PHONE DIALER

    fun openDialer(
        context: Context,
        phoneNumber: String
    ) {

        if (phoneNumber.isBlank()) return

        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.fromParts(
                "tel",
                phoneNumber,
                null
            )
        ).apply {

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Dialer unavailable.
        }
    }

    // SAVE NEW CONTACT

    fun addContact(
        context: Context,
        name: String,
        number: String
    ) {

        val intent = Intent(
            Intent.ACTION_INSERT
        ).apply {

            type = ContactsContract.Contacts.CONTENT_TYPE

            putExtra(
                ContactsContract.Intents.Insert.NAME,
                name
            )

            putExtra(
                ContactsContract.Intents.Insert.PHONE,
                number
            )

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Contacts application unavailable.
        }
    }

    // GET CONTACT BY EXACT NAME

    fun findExactContact(
        context: Context,
        name: String
    ): ContactInfo? {

        val matches = searchContact(
            context,
            name
        )

        return matches.firstOrNull {
            it.name.equals(
                name.trim(),
                ignoreCase = true
            )
        }
    }
}
