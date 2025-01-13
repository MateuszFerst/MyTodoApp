import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import com.ferst.mytodoapp.R

class RepeatTaskDialog(
    context: Context,
    private val onDaysSelected: (selectedDays: List<String>) -> Unit
) : Dialog(context) {

    private val selectedDays = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_repeat_task)

        val daysMap = mapOf(
            R.id.check_pn to "Pn",
            R.id.check_wt to "Wt",
            R.id.check_sr to "Śr",
            R.id.check_czw to "Czw",
            R.id.check_pt to "Pt",
            R.id.check_sb to "Sb",
            R.id.check_nd to "Nd"
        )

        val saveButton = findViewById<Button>(R.id.save_button)
        saveButton.setOnClickListener {
            selectedDays.clear()
            for ((id, day) in daysMap) {
                val checkBox = findViewById<CheckBox>(id)
                if (checkBox.isChecked) {
                    selectedDays.add(day)
                }
            }
            onDaysSelected(selectedDays)
            dismiss()
        }
    }
}
