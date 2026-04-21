package com.example.touchvision

//вспомогательный класс обертка для spinner

import android.view.View
import android.widget.AdapterView

class SimpleItemSelectedListener(private val onSelected: (String) -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val value = parent?.getItemAtPosition(position)?.toString() ?: return
        onSelected(value)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
}
