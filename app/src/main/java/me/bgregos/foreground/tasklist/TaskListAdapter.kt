package me.bgregos.foreground.tasklist

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.TaskListContentStandardBinding
import me.bgregos.foreground.model.Task
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(private val parentFragment: TaskListFragment,
                        private val viewModel: TaskViewModel) :
        ListAdapter<Task, TaskListAdapter.ViewHolder>(DiffCallback()) {

    private val onClickListener: OnClickListener = OnClickListener { v ->
        val task = v.tag as Task
        parentFragment.openTask(task)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Task>() {

        override fun areItemsTheSame(oldItem: Task, newItem: Task) =
            oldItem.uuid == newItem.uuid

        override fun areContentsTheSame(oldItem: Task, newItem: Task) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.task_list_content_standard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm aaa", Locale.getDefault())
        val item = getItem(position)
        holder.title.text = item.name
        if(item.dueDate != null) {
            holder.due.visibility = VISIBLE
            holder.dueicon.visibility = VISIBLE
            holder.due.text = format.format(item.dueDate as Date)
        }else{
            holder.dueicon.visibility = GONE
            holder.due.visibility = GONE
        }
        if(item.project.isNullOrEmpty()){
            holder.project.visibility = GONE
            holder.projecticon.visibility = GONE
            //remove margin from tags icon so it lines up with where this was
            val param = holder.tagsicon.layoutParams as ViewGroup.MarginLayoutParams
            param.marginStart = 0
            holder.tagsicon.layoutParams = param
        }else{
            holder.project.visibility = VISIBLE
            holder.projecticon.visibility = VISIBLE
            holder.project.text = item.project
        }
        if(item.tags.size == 0 || item.tags[0].isBlank()){
            holder.tags.visibility = GONE
            holder.tagsicon.visibility = GONE
        }else{
            holder.tags.visibility = VISIBLE
            holder.tagsicon.visibility = VISIBLE
            holder.tags.text = item.tags.joinToString(", ")
        }
        val color = ColorDrawable(when (item.priority) {
            "H" -> Color.parseColor("#550000")
            "M" -> Color.parseColor("#666666")
            "L" -> Color.parseColor("#303066")
            else -> Color.parseColor("#373737")
        })
        holder.accent.background = color

        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
        }

        holder.complete.setOnClickListener {
            viewModel.markTaskComplete(item)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binding = TaskListContentStandardBinding.bind(view)
        val title: TextView = binding.title
        val due: TextView = binding.due
        val dueicon: ImageView = binding.icDate
        val project: TextView = binding.project
        val projecticon: ImageView = binding.icProj
        val tags: TextView = binding.tags
        val tagsicon: ImageView = binding.icTags
        val accent: View = binding.taskListCardAccentbar
        val complete: ImageView = binding.complete
    }

}
