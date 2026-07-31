package com.example.billiardtracker.data.contacts

import android.content.ContentResolver
import android.provider.ContactsContract

data class Contact(val name: String, val phone: String)

class ContactsReader(private val resolver: ContentResolver) {
    fun readAll(): List<Contact> {
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        ) ?: return emptyList()

        val out = mutableListOf<Contact>()
        val seen = mutableSetOf<Pair<String, String>>()
        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val raw = it.getString(phoneIdx) ?: continue
                val phone = normalizePhone(raw)
                if (phone.isBlank()) continue
                val key = name.trim() to phone
                if (seen.add(key)) out += Contact(name.trim(), phone)
            }
        }
        return out
    }
}
