package edu.vt.cs.cs5254.dreamcatcher

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import edu.vt.cs.cs5254.dreamcatcher.databinding.DialogAddReflectionBinding

private const val REQUEST_KEY_ADD_REFLECTION = "request_key"
private const val BUNDLE_KEY_REFLECTION_TEXT = "reflection_text"

class AddReflectionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ui = DialogAddReflectionBinding.inflate(LayoutInflater.from(context))
        val okListener = DialogInterface.OnClickListener { _, _ ->
            parentFragmentManager.setFragmentResult(REQUEST_KEY_ADD_REFLECTION,
                bundleOf(Pair(BUNDLE_KEY_REFLECTION_TEXT,
                    ui.reflectionText.text.toString()))
            )
        }

        return AlertDialog.Builder(requireContext())
            .setView(ui.root)
            .setTitle("Add Reflection")
            .setPositiveButton(android.R.string.ok, okListener)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}