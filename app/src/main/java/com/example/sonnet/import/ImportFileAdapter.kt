package com.example.sonnet.import

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sonnet.R
import com.example.sonnet.models.ImportedFile
import com.example.sonnet.models.ImportStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImportFileAdapter(
    private val onDeleteClick: (Int, ImportedFile) -> Unit
) : RecyclerView.Adapter<ImportFileAdapter.FileViewHolder>() {

    private val files = mutableListOf<ImportedFile>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_imported_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position], position)
    }

    override fun getItemCount(): Int = files.size

    fun setFiles(newFiles: List<ImportedFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun addFile(file: ImportedFile) {
        files.add(file)
        notifyItemInserted(files.size - 1)
    }

    fun updateFileStatus(position: Int, newStatus: ImportStatus) {
        if (position in files.indices) {
            files[position] = files[position].copy(status = newStatus)
            notifyItemChanged(position)
        }
    }

    fun updateFile(position: Int, updatedFile: ImportedFile) {
        if (position in files.indices) {
            files[position] = updatedFile
            notifyItemChanged(position)
        }
    }

    fun removeFile(position: Int) {
        if (position in files.indices) {
            files.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, files.size)
        }
    }

    fun getFiles(): List<ImportedFile> = files.toList()

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)
        private val fileName: TextView = itemView.findViewById(R.id.file_name)
        private val importDate: TextView = itemView.findViewById(R.id.import_date)
        private val streamCount: TextView = itemView.findViewById(R.id.stream_count)
        private val progressIndicator: ProgressBar = itemView.findViewById(R.id.progress_indicator)
        private val statusIcon: ImageView = itemView.findViewById(R.id.status_icon)

        fun bind(file: ImportedFile, position: Int) {
            // Set file name
            fileName.text = file.fileName

            // Set import date (current time for newly imported files)
            val dateFormat = SimpleDateFormat("d/M/yy 'at' HH:mm", Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
            val currentDate = dateFormat.format(Date())
            importDate.text = "Imported on $currentDate"

            // Set stream count
            streamCount.text = "${formatNumber(file.validEntryCount)} verified streams"

            // Update UI based on status
            updateStatusUI(file.status)

            // Delete button click
            deleteButton.setOnClickListener {
                onDeleteClick(position, file)
            }
        }

        private fun updateStatusUI(status: ImportStatus) {
            when (status) {
                ImportStatus.PENDING -> {
                    // White text, no progress, no icon
                    fileName.setTextColor(Color.parseColor("#FFFFFF"))
                    progressIndicator.visibility = View.GONE
                    statusIcon.visibility = View.GONE
                }
                ImportStatus.PROCESSING -> {
                    // Yellow text, show progress, no icon
                    fileName.setTextColor(Color.parseColor("#E5D460"))
                    progressIndicator.visibility = View.VISIBLE
                    statusIcon.visibility = View.GONE
                }
                ImportStatus.COMPLETED -> {
                    // Green text, no progress, show success icon
                    fileName.setTextColor(Color.parseColor("#60E57C"))
                    progressIndicator.visibility = View.GONE
                    statusIcon.visibility = View.VISIBLE
                    statusIcon.setImageResource(R.drawable.ic_success)
                }
                ImportStatus.ERROR -> {
                    // Red text, no progress, show error icon
                    fileName.setTextColor(Color.parseColor("#FC5154"))
                    progressIndicator.visibility = View.GONE
                    statusIcon.visibility = View.VISIBLE
                    statusIcon.setImageResource(R.drawable.ic_error)
                }
            }
        }

        private fun formatNumber(number: Int): String {
            return String.format(Locale("es", "ES"), "%,d", number)
        }
    }
}
