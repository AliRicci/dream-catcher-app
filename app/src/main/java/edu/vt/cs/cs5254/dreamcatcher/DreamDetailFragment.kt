package edu.vt.cs.cs5254.dreamcatcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color.rgb
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.vt.cs.cs5254.dreamcatcher.databinding.FragmentDreamDetailBinding
import edu.vt.cs.cs5254.dreamcatcher.databinding.ListItemDreamEntryBinding
import java.io.File
import java.util.UUID
import java.util.Date


private const val TAG = "DreamDetailFragment"
private const val ARG_DREAM_ID = "dream_id"
private const val REQUEST_KEY_ADD_REFLECTION = "request_key"
private const val BUNDLE_KEY_REFLECTION_TEXT = "reflection_text"
private val CONCEIVED_BUTTON_COLOR = rgb(163, 0, 21) //ruby red
private val REFLECTION_BUTTON_COLOR = rgb(249, 87, 56) //orange soda
private val DEFERRED_BUTTON_COLOR = rgb(0, 159, 183) //pacific blue
private val FULFILLED_BUTTON_COLOR = rgb(89, 46, 131) //KSU purple

class DreamDetailFragment : Fragment() {

    interface Callbacks {
        fun onDreamSelected(dreamId: UUID)
    }

    private var callbacks: Callbacks? = null

    private lateinit var dreamWithEntries: DreamWithEntries
    private var _binding: FragmentDreamDetailBinding? = null
    private val binding get() = _binding!!
    private var adapter: DreamDetailFragment.DreamEntryAdapter? = DreamEntryAdapter(emptyList())
    private val viewModel: DreamDetailViewModel by viewModels()

    private lateinit var photoFile: File
    private lateinit var photoUri: Uri
    private lateinit var photoLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        dreamWithEntries = DreamWithEntries(Dream(), emptyList())
        val dreamId: UUID = arguments?.getSerializable(ARG_DREAM_ID) as UUID
        viewModel.loadDream(dreamId)
        photoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                updatePhotoView()
            }
            requireActivity().revokeUriPermission(photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        Log.d(TAG, "Dream detail fragment for dream ID $dreamId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDreamDetailBinding.inflate(inflater, container, false)
        val view = binding.root
        binding.dreamEntryRecyclerView.adapter = adapter
        binding.dreamEntryRecyclerView.layoutManager = LinearLayoutManager(context)
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(binding.dreamEntryRecyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.dreamLiveData.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { dreamWithEntries ->
                dreamWithEntries.let {
                    this.dreamWithEntries = dreamWithEntries
                    photoFile = viewModel.getPhotoFile(dreamWithEntries.dream)
                    photoUri = FileProvider.getUriForFile(
                        requireActivity(),
                        "edu.vt.cs.cs5254.dreamcatcher.fileprovider",
                        photoFile
                    )
                    refreshView()
                }
            }
        )

    }

    override fun onStart() {
        super.onStart()

        binding.dreamTitleText.doOnTextChanged { text, _, _, _ ->
            dreamWithEntries.dream.title = text.toString()
        }

        binding.dreamFulfilledCheckbox.setOnClickListener {
            onFulfilledClick()
            refreshView()
        }

        binding.dreamDeferredCheckbox.setOnClickListener {
            onDeferredClick()
            refreshView()
        }

        binding.addReflectionButton.setOnClickListener {
            AddReflectionDialog().show(parentFragmentManager, REQUEST_KEY_ADD_REFLECTION)
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ADD_REFLECTION,
            viewLifecycleOwner
        )
        { _, bundle ->
            addDreamEntry(
                dreamId = dreamWithEntries.dream.id,
                text = bundle.getSerializable(BUNDLE_KEY_REFLECTION_TEXT).toString(),
                kind = DreamEntryKind.REFLECTION
            )
            refreshView()
        }

    }

    private fun onFulfilledClick() {
        val entries = dreamWithEntries.dreamEntries.toList()
        if (entries.any { entry -> entry.kind == DreamEntryKind.FULFILLED }) {
            dreamWithEntries.dreamEntries = dreamWithEntries.dreamEntries.dropLast(1)
            dreamWithEntries.dream.isFulfilled = false
            binding.addReflectionButton.isEnabled = true
        } else {
            addDreamEntry(
                dreamId = dreamWithEntries.dream.id,
                text = "",
                kind = DreamEntryKind.FULFILLED
            )
            dreamWithEntries.dream.isFulfilled = true
            binding.addReflectionButton.isEnabled = false
        }
    }

    private fun onDeferredClick() {
        val entries = dreamWithEntries.dreamEntries.toList()
        if (entries.any { entry -> entry.kind == DreamEntryKind.DEFERRED }) {
            dreamWithEntries.dreamEntries =
                dreamWithEntries.dreamEntries.filterNot { entry -> entry.kind == DreamEntryKind.DEFERRED }
            dreamWithEntries.dream.isDeferred = false
        } else {
            addDreamEntry(
                dreamId = dreamWithEntries.dream.id,
                text = "",
                kind = DreamEntryKind.DEFERRED
            )
            dreamWithEntries.dream.isDeferred = true
        }
    }

    private fun addDreamEntry(dreamId: UUID, text: String, kind: DreamEntryKind) {
        val newDreamEntry = DreamEntry(
            id = UUID.randomUUID(),
            date = Date(),
            text = text,
            kind = kind,
            dreamId = dreamId
        )
        dreamWithEntries.dreamEntries += newDreamEntry
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveDream(dreamWithEntries)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Option menu callbacks
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_dream_detail, menu)
        val cameraAvailable = PictureUtils.isCameraAvailable(requireActivity())
        val menuItem = menu.findItem(R.id.take_dream_photo)
        menuItem.apply {
            Log.d(TAG, "Camera available: $cameraAvailable")
            isEnabled = cameraAvailable
            isVisible = cameraAvailable
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share_dream -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getDreamReport())
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.dream_report_subject)
                    )
                }.also { intent ->
                    val chooserIntent =
                        Intent.createChooser(intent, getString(R.string.send_report))
                    startActivity(chooserIntent)
                }
                true
            }
            R.id.take_dream_photo -> {
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }
                requireActivity().packageManager
                    .queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    .forEach { cameraActivity ->
                        requireActivity().grantUriPermission(
                            cameraActivity.activityInfo.packageName,
                            photoUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                photoLauncher.launch(photoUri)
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun getDreamReport(): String {
        val dreamTitle = dreamWithEntries.dream.title
        val df = DateFormat.getMediumDateFormat(context)
        val dreamConceivedDate = df.format(dreamWithEntries.dream.date).toString()
        val dreamReflection = getString(R.string.dream_reflections)
        val dreamReflectionList = printDreamEntry(dreamWithEntries)

        val dreamStatus = when {
            dreamWithEntries.dream.isDeferred -> {
                getString(R.string.dream_deferred_text)
            }
            dreamWithEntries.dream.isFulfilled -> {
                getString(R.string.dream_fulfilled_text)
            }
            else -> {
                ""
            }
        }
        return getString(
            R.string.dream_report,
            dreamTitle, dreamConceivedDate, dreamReflection, dreamReflectionList, dreamStatus
        )
    }

    private fun printDreamEntry(dreamWithEntries: DreamWithEntries): String {
        val dreamEntryReflection =
            dreamWithEntries.dreamEntries.filter { entry -> entry.kind == DreamEntryKind.REFLECTION }
        var dreamEntryString = ""
        if (dreamEntryReflection.isNotEmpty()) {
            dreamEntryReflection.forEach { dreamEntry ->
                dreamEntryString += "- ".plus(dreamEntry.text).plus("\n")
            }
        }
        dreamEntryString = dreamEntryString.dropLast(1)
        return dreamEntryString
    }

    inner class DreamEntryHolder(private val itemDreamEntryBinding: ListItemDreamEntryBinding) :
        RecyclerView.ViewHolder(itemDreamEntryBinding.root), View.OnClickListener {

        lateinit var dreamEntry: DreamEntry

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(dreamEntry: DreamEntry) {
            this.dreamEntry = dreamEntry
            refreshEntryButton(this.itemDreamEntryBinding.dreamEntryButton, dreamEntry)
        }

        private fun refreshEntryButton(button: Button, entry: DreamEntry) {

            val df = DateFormat.getMediumDateFormat(context)

            if (entry.kind == DreamEntryKind.CONCEIVED) {
                button.text = entry.kind.toString()
                button.setBackgroundColor(CONCEIVED_BUTTON_COLOR)
                button.visibility = View.VISIBLE
            }
            if (entry.kind == DreamEntryKind.REFLECTION) {
                button.text = df.format(dreamWithEntries.dream.date).plus(": ").plus(entry.text)
                button.setBackgroundColor(REFLECTION_BUTTON_COLOR)
                button.visibility = View.VISIBLE
            }
            if (entry.kind == DreamEntryKind.DEFERRED) {
                button.text = entry.kind.toString()
                button.setBackgroundColor(DEFERRED_BUTTON_COLOR)
                button.visibility = View.VISIBLE
            }
            if (entry.kind == DreamEntryKind.FULFILLED) {
                button.text = entry.kind.toString()
                button.setBackgroundColor(FULFILLED_BUTTON_COLOR)
                button.visibility = View.VISIBLE
            }
        }

        override fun onClick(v: View?) {
            callbacks?.onDreamSelected(dreamEntry.id)
        }
    }


    inner class DreamEntryAdapter(private var dreamEntries: List<DreamEntry>) :
        RecyclerView.Adapter<DreamDetailFragment.DreamEntryHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): DreamDetailFragment.DreamEntryHolder {
            val itemBinding =
                ListItemDreamEntryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            return DreamEntryHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: DreamDetailFragment.DreamEntryHolder, position: Int) {
            val dreamEntry = dreamEntries[position]
            holder.bind(dreamEntry)
        }

        override fun getItemCount() = dreamEntries.size

        fun deleteItem(position: Int) {
            Log.d(TAG, "deleteItem called on button position: $position")
            val dreamEntryToRemove = dreamEntries[position]
            dreamWithEntries.dreamEntries =
                dreamWithEntries.dreamEntries.filter { it.id != dreamEntryToRemove.id }
            notifyItemRemoved(position)
        }

    }

    inner class SwipeToDeleteCallback :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val dreamEntryHolder = viewHolder as DreamDetailFragment.DreamEntryHolder

            if (dreamEntryHolder.dreamEntry.kind == DreamEntryKind.REFLECTION) {
                val position = dreamEntryHolder.bindingAdapterPosition
                adapter?.deleteItem(position)
            }

            refreshView()
        }

    }

    companion object {
        fun newInstance(dreamId: UUID): DreamDetailFragment {
            val args = Bundle().apply {
                putSerializable(ARG_DREAM_ID, dreamId)
            }
            return DreamDetailFragment().apply {
                arguments = args
            }
        }
    }


    private fun refreshView() {

        // refresh title
        binding.dreamTitleText.setText(dreamWithEntries.dream.title)

        // refresh checkboxes
        when {
            dreamWithEntries.dream.isFulfilled -> {
                binding.dreamFulfilledCheckbox.isChecked = true
                binding.dreamFulfilledCheckbox.isEnabled = true
                binding.dreamDeferredCheckbox.isEnabled = false
            }
            dreamWithEntries.dream.isDeferred -> {
                binding.dreamDeferredCheckbox.isChecked = true
                binding.dreamFulfilledCheckbox.isEnabled = false
                binding.dreamDeferredCheckbox.isEnabled = true
            }
            else -> {
                binding.dreamFulfilledCheckbox.isEnabled = true
                binding.dreamDeferredCheckbox.isEnabled = true
                binding.dreamDeferredCheckbox.isChecked = false
                binding.dreamFulfilledCheckbox.isChecked = false
            }
        }

        val dreamEntries = dreamWithEntries.dreamEntries
        adapter = DreamEntryAdapter(dreamEntries)
        binding.dreamEntryRecyclerView.adapter = adapter

        updatePhotoView()
    }

    private fun updatePhotoView() {
        if (photoFile.exists()) {
            val bitmap = PictureUtils.getScaledBitmap(photoFile.path, 120, 120)
            binding.dreamPhoto.setImageBitmap(bitmap)
        } else {
            binding.dreamPhoto.setImageDrawable(null)
        }
    }

}





