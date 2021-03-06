package caventa.ansheer.ndk.caventa.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.github.kimkevin.cachepot.CachePot;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import caventa.ansheer.ndk.caventa.R;
import caventa.ansheer.ndk.caventa.commons.Activity_Utils;
import caventa.ansheer.ndk.caventa.commons.Date_Utils;
import caventa.ansheer.ndk.caventa.constants.General_Data;
import caventa.ansheer.ndk.caventa.models.Sales_Person;
import caventa.ansheer.ndk.caventa.models.sortable_table_view.account_ledger_table_view.Account_Ledger_Entry;
import caventa.ansheer.ndk.caventa.models.sortable_table_view.account_ledger_table_view.Account_Ledger_TableView;
import caventa.ansheer.ndk.caventa.models.sortable_table_view.account_ledger_table_view.Account_Ledger_Table_Data_Adapter;
import ndk.utils.Toast_Utils;


import static caventa.ansheer.ndk.caventa.commons.Date_Utils.mysql_date_time_format;

//TODO:Loans

public class Sales_Ledger extends AppCompatActivity {


    private Context application_context;
    static List<Account_Ledger_Entry> account_ledger_entries;
    private ProgressBar mProgressView;
    private Account_Ledger_TableView account_ledger_tableView;
    private Sales_Person selected_sales_person;
    private Spinner spinner_Scheme;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sales_ledger);
        initView();

        application_context = getApplicationContext();
        selected_sales_person = CachePot.getInstance().pop(Sales_Person.class);

        Toolbar mToolbar = findViewById(R.id.toolbar);
        spinner_Scheme = findViewById(R.id.spinner_nav);

        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }

        final boolean[] mSpinnerInitialized = new boolean[1];
        spinner_Scheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int item_position, long id) {
                //TODO : refresh on same scheme selection
                if (!mSpinnerInitialized[0]) {
                    mSpinnerInitialized[0] = true;
                    return;
                }

                if (isOnline()) {
                    load_sales_reports_page();
                } else {
                    Toast_Utils.longToast(getApplicationContext(), "Internet is unavailable");
                }


            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                // TODO : Your code here
            }
        });

        ArrayList<String> spinner_list = new ArrayList<>();

        for (int i = 0; i < Accounts.sales_persons.size(); i++) {
            spinner_list.add(Accounts.sales_persons.get(i).getName());
        }
        ArrayAdapter<String> spinner_adapter = new ArrayAdapter<String>(application_context, R.layout.spinner_item_actionbar, spinner_list);
        spinner_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_Scheme.setAdapter(spinner_adapter);

        spinner_Scheme.setSelection(getIntent().getIntExtra("position",0));

        if (load_account_ledger_task != null) {
            load_account_ledger_task.cancel(true);
            load_account_ledger_task = null;
        }
        showProgress(true);
        load_account_ledger_task = new Load_Account_Ledger_Task();
        load_account_ledger_task.execute((Void) null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.add_work, menu);

        // Associate searchable configuration with the SearchView
//        SearchManager searchManager =
//                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
//        SearchView searchView =
//                (SearchView) menu.findItem(R.id.search).getActionView();
//        searchView.setSearchableInfo(
//                searchManager.getSearchableInfo(getComponentName()));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.menu_item_save) {
//            Activity_Utils.start_activity(this, Accounts.class);
            if (create_Account_Ledger_Pdf())
                promptForNextAction();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    File myFile;

    boolean create_Account_Ledger_Pdf() {

        File docsFolder = new File(Environment.getExternalStorageDirectory() + "/Documents");
        boolean is_documents_Present = true;
        if (!docsFolder.exists()) {
            is_documents_Present = docsFolder.mkdir();
        }
        if (is_documents_Present) {
            File pdfFolder = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "Caventa Manager");
            boolean is_caventa_manager_Present = true;
            if (!pdfFolder.exists()) {
                is_caventa_manager_Present = pdfFolder.mkdir();

            }
            if (is_caventa_manager_Present) {

                Log.i(General_Data.TAG, "Pdf Directory created");

                //Create time stamp
                Date date = new Date();
                String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(date);
                myFile = new File(pdfFolder + "/Sales_" +selected_sales_person.getName()+"_"+ timeStamp + ".pdf");

                try {
                    OutputStream output = new FileOutputStream(myFile);

                    //Step 1
                    Document document = new Document(PageSize.A4);

                    //Step 2
                    PdfWriter.getInstance(document, output);

                    //Step 3
                    document.open();

                    //Step 4 Add content

                    Paragraph title = new Paragraph("Caventa Manager, Sales Ledger, "+selected_sales_person.getName(), FontFactory.getFont(FontFactory.TIMES_ROMAN, 16, Font.BOLD, BaseColor.BLACK));

                    addEmptyLine(title, 1);
                    title.setAlignment(Element.ALIGN_CENTER);
                    document.add(title);

                    PdfPTable table = new PdfPTable(5);

                    PdfPCell c1 = new PdfPCell(new Phrase("Date"));
                    c1.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c1);

                    PdfPCell c2 = new PdfPCell(new Phrase("Particulars"));
                    c2.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c2);

                    PdfPCell c3 = new PdfPCell(new Phrase("Debit"));
                    c3.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c3);

                    PdfPCell c4 = new PdfPCell(new Phrase("Credit"));
                    c4.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c4);

                    PdfPCell c5 = new PdfPCell(new Phrase("Balance"));
                    c5.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c5);

//                    table.setHeaderRows(1);

//                    table.addCell("1.1");
//                    table.addCell("1.2");
//                    table.addCell("1.3");

                    if (!account_ledger_entries.isEmpty()) {
                        for (Account_Ledger_Entry account_ledger_entry : account_ledger_entries) {
                            table.addCell(Date_Utils.normal_date_time_short_year_format.format(account_ledger_entry.getInsertion_date()));
                            table.addCell(account_ledger_entry.getParticulars());
                            table.addCell(String.valueOf(account_ledger_entry.getDebit_amount()));
                            table.addCell(String.valueOf(account_ledger_entry.getCredit_amount()));
                            table.addCell(String.valueOf(account_ledger_entry.getBalance()));
                        }
                    }

                    document.add(table);

                    //Step 5: Close the document
                    document.close();
                    return true;

                } catch (DocumentException | FileNotFoundException e) {
                    e.printStackTrace();
                    Log.i(General_Data.TAG, "Pdf Creation failure " + e.getLocalizedMessage());
                    Toast_Utils.longToast(application_context, "Pdf fail");
                }

            } else {
                Log.i(General_Data.TAG, "Folder Creation failure ");
                Toast_Utils.longToast(application_context, "Folder fail");
            }

        } else {
            Log.i(General_Data.TAG, "Folder Creation failure ");
            Toast_Utils.longToast(application_context, "Folder fail");
        }
        return false;
    }

    private static void addEmptyLine(Paragraph paragraph, int number) {
        for (int i = 0; i < number; i++) {
            paragraph.add(new Paragraph(" "));
        }
    }

    private void promptForNextAction() {
        final String[] options = {
                "Preview It",
                "Cancel"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ledger Saved, What Next?");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (options[which].equals("Email It")) {
                    emailNote();
                } else if (options[which].equals("Preview It")) {
                    viewPdf();
                } else if (options[which].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });

        builder.show();

    }


    private void emailNote() {
        Intent email = new Intent(Intent.ACTION_SEND);
        email.putExtra(Intent.EXTRA_SUBJECT, "mSubjectEditText.getText().toString()");
        email.putExtra(Intent.EXTRA_TEXT, "mBodyEditText.getText().toString()");
        Uri uri = Uri.parse(myFile.getAbsolutePath());
        email.putExtra(Intent.EXTRA_STREAM, uri);
        email.setType("message/rfc822");
        startActivity(email);
    }

    private void viewPdf() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(myFile), "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    private void load_sales_reports_page() {
//        Intent i = new Intent(application_context, Sales_Reports.class);
//        CachePot.getInstance().push(Accounts.sales_persons.get(spinner_Scheme.getSelectedItemPosition()));
//        i.putExtra("position", spinner_Scheme.getSelectedItemPosition());
//        startActivity(i);
//        finish();

        Activity_Utils.start_activity_with_object_push_and_integer_extras_and_finish(this, Sales_Ledger.class, new Pair[]{new Pair("position", spinner_Scheme.getSelectedItemPosition())}, Accounts.sales_persons.get(spinner_Scheme.getSelectedItemPosition()));

    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private Load_Account_Ledger_Task load_account_ledger_task = null;

    private void initView() {
        mProgressView = findViewById(R.id.login_progress);
        account_ledger_tableView = findViewById(R.id.tableView);

    }

    public class Load_Account_Ledger_Task extends AsyncTask<Void, Void, String[]> {
        Load_Account_Ledger_Task() {
        }

        DefaultHttpClient http_client;
        HttpPost http_post;
        String network_action_response;
        ArrayList<NameValuePair> name_pair_value_array;

        @Override
        protected String[] doInBackground(Void... params) {
            try {
                http_client = new DefaultHttpClient();
                http_post = new HttpPost(General_Data.SERVER_IP_ADDRESS + "/android/get_sales_ledger.php");
                name_pair_value_array = new ArrayList<>(1);
                name_pair_value_array.add(new BasicNameValuePair("sales_person_id", selected_sales_person.getId()));
                http_post.setEntity(new UrlEncodedFormEntity(name_pair_value_array));
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
            load_account_ledger_task = null;

            showProgress(false);
            account_ledger_entries = new ArrayList<>();

            Log.d(General_Data.TAG, network_action_response_array[0]);
            Log.d(General_Data.TAG, network_action_response_array[1]);

            if (network_action_response_array[0].equals("1")) {
                Toast.makeText(application_context, "Error : " + network_action_response_array[1], Toast.LENGTH_LONG).show();
                Log.d(General_Data.TAG, network_action_response_array[1]);
            } else {


                try {

                    JSONArray json_array = new JSONArray(network_action_response_array[1]);
                    if (json_array.getJSONObject(0).getString("status").equals("1")) {
                        Toast.makeText(application_context, "Error...", Toast.LENGTH_LONG).show();
                    } else if (json_array.getJSONObject(0).getString("status").equals("0")) {



                        double balance = 0;
                        for (int i = 1; i < json_array.length(); i++) {

                            if (json_array.getJSONObject(i).getString("particulars").contains("~Advance")) {
                                balance = balance + Double.parseDouble(json_array.getJSONObject(i).getString("amount"));
                                account_ledger_entries.add(new Account_Ledger_Entry(mysql_date_time_format.parse(json_array.getJSONObject(i).getString("insertion_date_time")), json_array.getJSONObject(i).getString("particulars"), 0, Double.parseDouble(json_array.getJSONObject(i).getString("amount")), balance));
                                Log.d(General_Data.TAG, String.valueOf(balance));
                            }
                            if (json_array.getJSONObject(i).getString("particulars").contains("~Expense") || json_array.getJSONObject(i).getString("particulars").contains("~Commission")) {
                                balance = balance - Double.parseDouble(json_array.getJSONObject(i).getString("amount"));
                                account_ledger_entries.add(new Account_Ledger_Entry(mysql_date_time_format.parse(json_array.getJSONObject(i).getString("insertion_date_time")), json_array.getJSONObject(i).getString("particulars"), Double.parseDouble(json_array.getJSONObject(i).getString("amount")), 0, balance));
                                Log.d(General_Data.TAG, String.valueOf(balance));
                            }


                        }

                        if (account_ledger_tableView != null) {
                            final Account_Ledger_Table_Data_Adapter account_ledger_table_data_adapter = new Account_Ledger_Table_Data_Adapter(getApplicationContext(), account_ledger_entries, account_ledger_tableView);
                            account_ledger_tableView.setDataAdapter(account_ledger_table_data_adapter);

                        }
                    }


                } catch (JSONException e) {
                    Toast.makeText(application_context, "Error : " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    Log.d(General_Data.TAG, e.getLocalizedMessage());
                } catch (ParseException e) {

                    Toast.makeText(application_context, "Date Error : " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    Log.d(General_Data.TAG, e.getLocalizedMessage());
                }


            }


        }

        @Override
        protected void onCancelled() {
            load_account_ledger_task = null;
            showProgress(false);
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        account_ledger_tableView.setVisibility(show ? View.GONE : View.VISIBLE);
        account_ledger_tableView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                account_ledger_tableView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }
}
