package caventa.ansheer.ndk.caventa.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.kimkevin.cachepot.CachePot;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import caventa.ansheer.ndk.caventa.R;
import caventa.ansheer.ndk.caventa.adapters.Work_Advances_View_Adapter;
import caventa.ansheer.ndk.caventa.adapters.Work_Expense_View_Adapter;
import caventa.ansheer.ndk.caventa.commons.Activity_Utils;
import caventa.ansheer.ndk.caventa.commons.Snackbar_Utils;
import caventa.ansheer.ndk.caventa.commons.TODO_Utils;
import caventa.ansheer.ndk.caventa.constants.General_Data;
import caventa.ansheer.ndk.caventa.models.Work;
import caventa.ansheer.ndk.caventa.models.Work_Advance;
import caventa.ansheer.ndk.caventa.models.Work_Expense;
import ndk.utils.Date_Utils;
import ndk.utils.Toast_Utils;


import static caventa.ansheer.ndk.caventa.commons.Network_Utils.isOnline;
import static caventa.ansheer.ndk.caventa.commons.Network_Utils.showProgress;
import static caventa.ansheer.ndk.caventa.commons.Visibility_Utils.set_visible;

//TODO:Cancel work

public class View_Work extends AppCompatActivity {

    private Context application_context;
    private View mProgressView;
    private View mLoginFormView;

    private Work_Advances_View_Adapter work_advances_adapter;

    private Work_Expense_View_Adapter work_expenses_adapter;

    static List<Work_Advance> work_advances;

    static List<Work_Expense> work_expenses;

    private TextView txt_name, txt_address, txt_total_advance, txt_total_expense, txt_profit;
    Work selected_work;
    private TextView txt_commission;
    private TextView txt_net_profit;
    private Context activity_context;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_work);
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        application_context = getApplicationContext();
        activity_context=this;

        selected_work = CachePot.getInstance().pop(Work.class);
        setTitle(selected_work.getWork_name());

        work_advances = new ArrayList<>();
        work_advances_adapter = new Work_Advances_View_Adapter(this, work_advances);

        RecyclerView work_advances_recycler_view = findViewById(R.id.recycler_view_advance);

        work_advances_recycler_view.setHasFixedSize(false);

        // use a linear layout manager
        LinearLayoutManager work_advances_recycler_view_layout_manager = new LinearLayoutManager(this);
        work_advances_recycler_view.setLayoutManager(work_advances_recycler_view_layout_manager);

        work_advances_recycler_view.setAdapter(work_advances_adapter);

        work_expenses = new ArrayList<>();
        work_expenses_adapter = new Work_Expense_View_Adapter(this, work_expenses);

        RecyclerView work_expenses_recycler_view = findViewById(R.id.recycler_view_expense);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        work_expenses_recycler_view.setHasFixedSize(false);

        // use a linear layout manager
        LinearLayoutManager work_expenses_recycler_view_layout_manager = new LinearLayoutManager(this);
        work_expenses_recycler_view.setLayoutManager(work_expenses_recycler_view_layout_manager);

        work_expenses_recycler_view.setAdapter(work_expenses_adapter);

        if (load_work_profit_task != null) {
            load_work_profit_task.cancel(true);
            load_work_profit_task = null;
        }
        showProgress(true, application_context, mProgressView, mLoginFormView);
        load_work_profit_task = new Load_Work_Profit_Task(this);
        load_work_profit_task.execute((Void) null);

        TextView txt_date = findViewById(R.id.work_date);
        txt_date.setText(Date_Utils.normal_Date_Format_words.format(selected_work.getWork_date()));

        initView();

        txt_name.setText(selected_work.getWork_name());
        txt_address.setText(selected_work.getWork_address());

        if (((getIntent().getExtras().getString("origin").equals("Sales_Person_Fin")) || (getIntent().getExtras().getString("origin").equals("Common_Fin"))) && (selected_work.getSales_person_id() != 1)) {
            set_visible(new View[]{txt_commission, txt_net_profit});
//            remove_from_parent_layout(new View[]{txt_commission,txt_net_profit});
        }
    }

    private void initView() {
        txt_name = findViewById(R.id.name);
        txt_address = findViewById(R.id.address);
        txt_total_advance = findViewById(R.id.total_advance);
        txt_total_expense = findViewById(R.id.total_expense);
        txt_profit = findViewById(R.id.total_profit);
        txt_commission = findViewById(R.id.commision);
        txt_net_profit = findViewById(R.id.net_profit);
    }

    private Load_Work_Profit_Task load_work_profit_task = null;

    public class Load_Work_Profit_Task extends AsyncTask<Void, Void, String[]> {
        private ArrayList<NameValuePair> name_pair_value;

        private double total_advance = 0;
        private double total_expense = 0;
        private boolean work_advances_flag = true;
        AppCompatActivity current_activity;

        Load_Work_Profit_Task(AppCompatActivity current_activity) {
            this.current_activity = current_activity;
        }

        DefaultHttpClient http_client;
        HttpPost http_post;
        String network_action_response;

        @Override
        protected String[] doInBackground(Void... params) {
            try {
                http_client = new DefaultHttpClient();
                http_post = new HttpPost(General_Data.SERVER_IP_ADDRESS + "/android/get_work.php");

                name_pair_value = new ArrayList<NameValuePair>(1);
                name_pair_value.add(new BasicNameValuePair("work_id", selected_work.getId()));

                http_post.setEntity(new UrlEncodedFormEntity(name_pair_value));

                ResponseHandler<String> response_handler = new BasicResponseHandler();
                network_action_response = http_client.execute(http_post, response_handler);
                return new String[]{"0", network_action_response};

            } catch (UnsupportedEncodingException e) {
                return new String[]{"1", "UnsupportedEncodingException : " + e.getLocalizedMessage()};
            } catch (ClientProtocolException e) {
                return new String[]{"1", "ClientProtocolException : " + e.getLocalizedMessage()};
            } catch (IOException e) {
                return new String[]{"1", "IOException : " + e.getLocalizedMessage()};
            }
        }


        @Override
        protected void onPostExecute(final String[] network_action_response_array) {
            load_work_profit_task = null;

            showProgress(false, application_context, mProgressView, mLoginFormView);

            Log.d(General_Data.TAG, network_action_response_array[0]);
            Log.d(General_Data.TAG, network_action_response_array[1]);

            if (network_action_response_array[0].equals("1")) {
                Toast.makeText(application_context, "Error : " + network_action_response_array[1], Toast.LENGTH_LONG).show();
                Log.d(General_Data.TAG, network_action_response_array[1]);
            } else {

                JSONArray json_array = new JSONArray();
                int i = 0;
                try {
                    json_array = new JSONArray(network_action_response_array[1]);
                    for (i = 0; i < json_array.length(); i++) {
                        if (json_array.getJSONObject(0).getString("status").equals("1")) {
                            Snackbar_Utils.display_Short_no_FAB_warning_bottom_SnackBar(current_activity, "No Advances...");
                            work_advances_flag = false;
                        } else {
                            if (i != 0 && work_advances_flag) {

                                work_advances.add(new Work_Advance(json_array.getJSONObject(i).getDouble("amount"), json_array.getJSONObject(i).getString("advance_description")));
                                total_advance = total_advance + json_array.getJSONObject(i).getDouble("amount");

                            }
                        }

                    }


                } catch (JSONException e) {

                    Log.d(General_Data.TAG, String.valueOf(i));

                    if (e.getLocalizedMessage().contains("No value for amount")) {
                        try {
                            for (int j = i; j < json_array.length(); j++) {
                                if (json_array.getJSONObject(i).getString("status").equals("1")) {
                                    Snackbar_Utils.display_Short_no_FAB_warning_bottom_SnackBar(current_activity, "No Expenses...");
                                    break;
                                } else {
                                    if (j != i) {
                                        work_expenses.add(new Work_Expense(json_array.getJSONObject(j).getDouble("amount"), json_array.getJSONObject(j).getString("expense_description")));
                                        total_expense = total_expense + json_array.getJSONObject(j).getDouble("amount");
                                    }
                                }
                            }

                            work_advances_adapter.notifyDataSetChanged();
                            txt_total_advance.setText("Advances : " + total_advance);

                            work_expenses_adapter.notifyDataSetChanged();
                            txt_total_expense.setText("Expenses : " + total_expense);

                            txt_profit.setText("Profit : " + (total_advance - total_expense));

                            txt_commission.setText("Commission : " + ((total_advance - total_expense) * 0.6));

                            txt_net_profit.setText("Net Profit : " + ((total_advance - total_expense) * 0.4));


                        } catch (JSONException ex) {
                            Toast.makeText(application_context, "Error : " + ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                            Log.d(General_Data.TAG, ex.getLocalizedMessage());
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(application_context, "Error : " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        Log.d(General_Data.TAG, e.getLocalizedMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            load_work_profit_task = null;
            showProgress(false, application_context, mProgressView, mLoginFormView);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getIntent().getExtras().getString("origin").equals("Sales_Person_Pen")) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.view_work_pen, menu);
        } else if (getIntent().getExtras().getString("origin").equals("Sales_Person_Up")) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.view_work_up, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.menu_item_finish) {

            AlertDialog.Builder after_time_dialog = new AlertDialog.Builder(this);
            after_time_dialog.setMessage("Work is Finished, Is it?").setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            dialog.cancel();
                            if (finish_work_task != null) {
                                return;
                            }

                            // Show a progress spinner, and kick off a background task to perform the user login attempt.
                            if (isOnline(application_context)) {
                                showProgress(true, application_context, mProgressView, mLoginFormView);
                                finish_work_task = new Finish_Work_Task(selected_work.getId());
                                finish_work_task.execute((Void) null);
                            } else {
                                Toast_Utils.longToast(getApplicationContext(), "Internet is unavailable");
                            }
                        }
                    }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                    dialog.cancel();
                }
            });
            AlertDialog alert = after_time_dialog.create();
            alert.setTitle("Warning!");
            alert.show();

            return true;
        }

        if (id == R.id.menu_item_work_cancel) {

            AlertDialog.Builder after_time_dialog = new AlertDialog.Builder(this);
            after_time_dialog.setMessage("Work will be cancelled, OK?").setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            dialog.cancel();
                            TODO_Utils.display_TODO_no_FAB_SnackBar(activity_context);


                        }
                    }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                    dialog.cancel();
                }
            });
            AlertDialog alert = after_time_dialog.create();
            alert.setTitle("Warning!");
            alert.show();

            return true;
        }

        if (id == R.id.menu_item_edit) {

            Intent i = new Intent(application_context, Edit_Work.class);
            CachePot.getInstance().push(selected_work);
            startActivity(i);
            finish();
//            TODO_Utils.display_TODO_no_FAB_SnackBar(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        switch (getIntent().getExtras().getString("origin")) {
            case "Sales_Person_Up":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Sales_Person_Dashboard_Page.class, 0);
                break;
            case "Sales_Person_Pen":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Sales_Person_Dashboard_Page.class, 1);
                break;
            case "Sales_Person_Fin":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Sales_Person_Dashboard_Page.class, 2);
                break;
            case "Common_Up":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Dashboard_Page.class, 0);
//                super.onBackPressed();
                break;
            case "Common_Pen":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Dashboard_Page.class, 1);
//                super.onBackPressed();
                break;
            case "Common_Fin":
                Activity_Utils.start_activity_with_finish_and_tab_index(this, Dashboard_Page.class, 2);
//                super.onBackPressed();
                break;
            default:
                super.onBackPressed();
        }
    }

    /* Keep track of the login task to ensure we can cancel it if requested. */
    private Finish_Work_Task finish_work_task = null;

    public class Finish_Work_Task extends AsyncTask<Void, Void, String[]> {

        String task_work_id;

        Finish_Work_Task(String work_id) {
            task_work_id = work_id;

        }

        DefaultHttpClient http_client;
        HttpPost http_post;
        ArrayList<NameValuePair> name_pair_value;
        String network_action_response;

        @Override
        protected String[] doInBackground(Void... params) {
            try {
                http_client = new DefaultHttpClient();
                http_post = new HttpPost(General_Data.SERVER_IP_ADDRESS + "/android/finish_work.php");
                name_pair_value = new ArrayList<NameValuePair>(1);
                name_pair_value.add(new BasicNameValuePair("work_id", task_work_id));

                http_post.setEntity(new UrlEncodedFormEntity(name_pair_value));
                ResponseHandler<String> response_handler = new BasicResponseHandler();
                network_action_response = http_client.execute(http_post, response_handler);
                return new String[]{"0", network_action_response};

            } catch (UnsupportedEncodingException e) {
                return new String[]{"1", "UnsupportedEncodingException : " + e.getLocalizedMessage()};
            } catch (ClientProtocolException e) {
                return new String[]{"1", "ClientProtocolException : " + e.getLocalizedMessage()};
            } catch (IOException e) {
                return new String[]{"1", "IOException : " + e.getLocalizedMessage()};
            }
        }


        @Override
        protected void onPostExecute(final String[] network_action_response_array) {
            finish_work_task = null;

            showProgress(false, application_context, mProgressView, mLoginFormView);

            Log.d(General_Data.TAG, network_action_response_array[0]);
            Log.d(General_Data.TAG, network_action_response_array[1]);

            if (network_action_response_array[0].equals("1")) {
                Toast.makeText(application_context, "Error : " + network_action_response_array[1], Toast.LENGTH_LONG).show();
                Log.d(General_Data.TAG, network_action_response_array[1]);
            } else {
                try {
                    JSONObject json = new JSONObject(network_action_response_array[1]);
                    String count = json.getString("status");
                    switch (count) {
                        case "0":
                            Toast.makeText(application_context, "OK", Toast.LENGTH_LONG).show();

//                            Activity_Utils.start_activity_with_finish_and_tab_index(activity_context,Sales_Person_Dashboard_Page.class,0);

                            Activity_Utils.start_activity_with_finish_and_tab_index(activity_context, Sales_Person_Dashboard_Page.class, 2);

                            break;
                        case "1":
                            Toast.makeText(application_context, "Error : " + json.getString("error"), Toast.LENGTH_LONG).show();
                            txt_name.requestFocus();
                            break;
                        default:
                            Toast.makeText(application_context, "Error : Check json", Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    Toast.makeText(application_context, "Error : " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    Log.d(General_Data.TAG, e.getLocalizedMessage());
                }
            }
        }

        @Override
        protected void onCancelled() {
            finish_work_task = null;
            showProgress(false, application_context, mProgressView, mLoginFormView);
        }
    }

}
