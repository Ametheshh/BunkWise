package com.example.attendanceportal;

import com.example.mini_project.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistoryActivity extends BaseActivity {

    private static final int LIMIT = 1000;

    private AttendanceRepo repo;
    private Spinner spSubject, spStatus, spMonth;
    private TextView tvCount;
    private ListView listHistory;
    private HistoryAdapter adapter;

    private final List<String> subjectCodes = new ArrayList<>();    // parallel to the subject spinner
    private final List<String> monthValues = new ArrayList<>();     // parallel to the month spinner
    private final String[] statusValues = {"", "Present", "Absent"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;
        setContentView(R.layout.activity_history);

        repo = new AttendanceRepo(this);
        spSubject = findViewById(R.id.spSubject);
        spStatus = findViewById(R.id.spStatus);
        spMonth = findViewById(R.id.spMonth);
        tvCount = findViewById(R.id.tvCount);
        listHistory = findViewById(R.id.listHistory);

        // subject filter (only this student's subjects)
        List<String> subjectLabels = new ArrayList<>();
        subjectLabels.add("All subjects");
        subjectCodes.add("");
        for (Models.SubjectSummary s : repo.getSubjects()) {
            subjectLabels.add(s.name);
            subjectCodes.add(s.code);
        }
        setSpinner(spSubject, subjectLabels);

        setSpinner(spStatus, Arrays.asList("All statuses", "Present", "Absent"));

        List<String> monthLabels = new ArrayList<>();
        monthLabels.add("All months");
        monthValues.add("");
        for (String m : repo.getMonths()) {
            monthLabels.add(Ui.niceMonth(m));
            monthValues.add(m);
        }
        setSpinner(spMonth, monthLabels);

        adapter = new HistoryAdapter();
        listHistory.setAdapter(adapter);

        AdapterView.OnItemSelectedListener reload = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        spSubject.setOnItemSelectedListener(reload);
        spStatus.setOnItemSelectedListener(reload);
        spMonth.setOnItemSelectedListener(reload);
        reload();
    }

    private void setSpinner(Spinner spinner, List<String> labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
    }

    private void reload() {
        if (!Session.isActive()) return;
        int si = Math.max(spSubject.getSelectedItemPosition(), 0);
        int ti = Math.max(spStatus.getSelectedItemPosition(), 0);
        int mi = Math.max(spMonth.getSelectedItemPosition(), 0);
        List<Models.HistoryItem> items = repo.getHistory(
                subjectCodes.get(si), statusValues[ti], monthValues.get(mi), LIMIT);
        adapter.setItems(items);
        tvCount.setText(items.size() + (items.size() == 1 ? " record" : " records"));
    }

    private class HistoryAdapter extends BaseAdapter {
        private List<Models.HistoryItem> items = new ArrayList<>();

        void setItems(List<Models.HistoryItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(HistoryActivity.this).inflate(R.layout.item_history_row, parent, false);
            Ui.bindHistoryRow(row, items.get(position));
            return row;
        }
    }
}
