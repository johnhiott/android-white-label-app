package com.votinginfoproject.VotingInformationProject.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.votinginfoproject.VotingInformationProject.R;
import com.votinginfoproject.VotingInformationProject.activities.HomeActivity;
import com.votinginfoproject.VotingInformationProject.asynctasks.CivicInfoApiQuery;
import com.votinginfoproject.VotingInformationProject.models.CivicApiError;
import com.votinginfoproject.VotingInformationProject.models.Contest;
import com.votinginfoproject.VotingInformationProject.models.Election;
import com.votinginfoproject.VotingInformationProject.models.VIPAppContext;
import com.votinginfoproject.VotingInformationProject.models.VoterInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;


public class HomeFragment extends Fragment {

    Button homeGoButton;
    CivicInfoApiQuery.CallBackListener voterInfoListener;
    CivicInfoApiQuery.CallBackListener voterInfoErrorListener;
    HomeActivity myActivity;
    Context context;
    Resources resources;
    EditText homeEditTextAddress;
    TextView homeTextViewStatus;
    Spinner homeElectionSpinner;
    View homeElectionSpinnerWrapper;
    Spinner homePartySpinner;
    View homePartySpinnerWrapper;
    ImageView homeSelectContactButton;

    Election currentElection;
    String address;
    SharedPreferences preferences;
    OnInteractionListener mListener;
    boolean isTest;

    /**
     * For use when testing only.  Sets flag to indicate that we're testing the app, so it will
     * use the special test election ID for the query.
     */
    public void doTestRun() {
        isTest = true;
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myActivity = (HomeActivity)getActivity();
        preferences = myActivity.getPreferences(Context.MODE_PRIVATE);
        currentElection = new Election();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);
        context = myActivity.getApplicationContext();
        resources = context.getResources();

        // read flag from api_keys file for whether to use test election or not
        isTest = resources.getBoolean(R.bool.use_test_election);

        homeTextViewStatus = (TextView)rootView.findViewById(R.id.home_textview_status);

        homeGoButton = (Button)rootView.findViewById(R.id.home_go_button);

        homeSelectContactButton = (ImageView)rootView.findViewById(R.id.home_select_contact_button);

        homeEditTextAddress = (EditText)rootView.findViewById(R.id.home_edittext_address);
        homeEditTextAddress.setText(getAddress());

        homeElectionSpinner = (Spinner)rootView.findViewById(R.id.home_election_spinner);
        homeElectionSpinnerWrapper = rootView.findViewById(R.id.home_election_spinner_wrapper);

        homePartySpinner = (Spinner)rootView.findViewById(R.id.home_party_spinner);
        homePartySpinnerWrapper = rootView.findViewById(R.id.home_party_spinner_wrapper);

        setupViewListeners();
        setupCivicAPIListeners();

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Helper function to check if address has changed, and either re-query if it has changed,
     * or fetch the last election from shared preferences if it hasn't.
     */
    public void makeElectionQuery() {
        String new_address = homeEditTextAddress.getText().toString();
        if (new_address.equals(address)) {
            Log.d("HomeFragment", "Address has not changed.");
            getElectionFromPreferences();
        } else {
            Log.d("HomeFragment", "Searching with changed address.");
            queryWithNewAddress(new_address);
        }
    }

    /**
     * Fetch the last election from shared preferences if a search performed without a change in
     * the entered address.
     */
    public void getElectionFromPreferences() {
        // show loading text
        homeTextViewStatus.setText(R.string.home_status_loading);
        homeTextViewStatus.setVisibility(View.VISIBLE);

        String lastElection = preferences.getString(resources.getString(R.string.LAST_ELECTION_KEY), "");
        if (lastElection.isEmpty()) {
            Log.e("HomeFragment", "Could not find last election in preferences!");
            queryWithNewAddress(address);
            return;
        }

        // have last election in preferences; re-hydrate it
        try {
            Gson gson = new GsonBuilder().create();
            VoterInfo voterInfo = gson.fromJson(lastElection, VoterInfo.class);
            Log.d("HomeFragment", "Got voter info result from shared preferences.");
            // check if stored election has passed yet
            if (voterInfo.election.isElectionOver()) {
                Log.d("HomeFragment", "Election in shared preferences is over; re-querying.");
                queryWithNewAddress(address);
            } else {
                Log.d("HomeFragment", "Election in shared preferences is still valid; using it.");
                presentVoterInfoResult(voterInfo);
            }
        } catch (Exception ex) {
            Log.e("HomeFragment", "Failed to re-hydrate last election!");
            ex.printStackTrace();
            queryWithNewAddress(address);
        }

    }

    /** Query the Civic Info API after the entered address has changed, and store the address
     * and election result to shared preferences.
     *
     * @param new_address New address entered
     */
    private void queryWithNewAddress(String new_address) {
        Log.d("HomeFragment", "queryWithNewAddress");
        setAddress(new_address);
        // clear previous election before making a query for a new address
        mListener.searchedAddress(null);
        currentElection = null;
        myActivity.setSelectedParty("");
        // only hide election picker when searching with a new address
        homeElectionSpinnerWrapper.setVisibility(View.GONE);
        homeGoButton.setVisibility(View.GONE);
        constructVoterInfoQuery();
    }

    private void setupViewListeners() {
        // Go Button onClick Listener
        homeGoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onGoButtonPressed(view);
                }
            }
        });

        // EditText image button onClick listener
        homeSelectContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onSelectContactButtonPressed(view);
                }
            }
        });

        // EditText onSearch Listener
        homeEditTextAddress.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                    && mListener != null) {
                    makeElectionQuery();
                }
                // Return false to close the keyboard
                return false;
            }
        });

        // election spinner listener
        homeElectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {

                Election selectedElection = (Election) adapterView.getItemAtPosition(index);
                Log.d("HomeFragment", "Selected via election picker: " + selectedElection.toString());
                // Only fire a new voterInfo query if the election changes
                if (!selectedElection.getId().equals(currentElection.getId())) {
                    currentElection = selectedElection;
                    constructVoterInfoQuery();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // PASS
            }
        });

        // party spinner listener
        homePartySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                myActivity.setSelectedParty((String) adapterView.getItemAtPosition(index));
                Log.d("HomeFragment", "Selected via party picker: " + myActivity.getSelectedParty());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // PASS
            }
        });
    }

    /**
     * Check for Internet connectivity before querying API.  If the Internet is unavailable or
     * disconnected, display a message and quit the app.
     */
    public void checkInternetConnectivity() {
        Context context = VIPAppContext.getContext();
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnectedOrConnecting()) {
            homeGoButton.setVisibility(View.INVISIBLE);
            homeTextViewStatus.setText(context.getResources().getText(R.string.home_error_no_internet));
        }
    }
    private void constructVoterInfoQuery() {
        checkInternetConnectivity(); // check for connection before querying

        String electionId = "";

        if (isTest) {
            electionId = "2000"; // test election ID (for use only in testing)
        }

        try {
            electionId = currentElection.getId();
        } catch (NullPointerException e) {}

        try {
            Uri.Builder builder = new Uri.Builder();
            builder.scheme("https").authority("www.googleapis.com").appendPath("civicinfo");
            builder.appendPath(resources.getString(R.string.civic_info_api_version));
            String officialOnly = resources.getBoolean(R.bool.civic_info_official_only) ? "true" : "false";
            builder.appendPath("voterinfo").appendQueryParameter("officialOnly", officialOnly);

            // set flag to view pre-production data
            if (resources.getBoolean(R.bool.use_preproduction)) {
                builder.appendQueryParameter("productionDataOnly", "false");
            }

            if (!electionId.isEmpty()) {
                builder.appendQueryParameter("electionId", electionId);
            }
            builder.appendQueryParameter("address", address);
            builder.appendQueryParameter("key", resources.getString(R.string.google_api_browser_key));
            String apiUrl = builder.build().toString();
            Log.d("HomeActivity", "searchedAddress: " + apiUrl);

            homePartySpinnerWrapper.setVisibility(View.GONE);

            // show loading text
            homeTextViewStatus.setText(R.string.home_status_loading);
            homeTextViewStatus.setVisibility(View.VISIBLE);

            // Make query
            new CivicInfoApiQuery<VoterInfo>(VoterInfo.class, voterInfoListener, voterInfoErrorListener,
                    preferences, resources.getString(R.string.LAST_ELECTION_KEY)).execute(apiUrl);
        } catch (Exception e) {
            Log.e("HomeActivity Exception", "searchedAddress: " + address);
        }
    }

    private void presentVoterInfoResult(VoterInfo voterInfo) {
        currentElection = voterInfo.election;
        homeTextViewStatus.setVisibility(View.GONE);
        homeGoButton.setVisibility(View.VISIBLE);

        // read Go button to user, if TalkBack enabled
        homeGoButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        mListener.searchedAddress(voterInfo);

        // Show election picker if there are other elections
        ArrayList<Election> elections = new ArrayList<Election>(voterInfo.otherElections);
        elections.add(0, voterInfo.election);

        setSpinnerElections(elections);
        setSpinnerParty(voterInfo.contests);
    }

    private void setupCivicAPIListeners() {
        // Callback for voterInfoQuery result
        voterInfoListener = new CivicInfoApiQuery.CallBackListener() {
            @Override
            public void callback(Object result) {
                if (result == null) {
                    // if query returns null, then CivicInfoApiQuery had an exception
                    Log.e("HomeFragment", "Got null result from voterInfoQuery!");
                    homeTextViewStatus.setText(R.string.home_error_unknown);
                    // read error result, if TalkBack enabled
                    homeTextViewStatus.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    return;
                }

                VoterInfo voterInfo = (VoterInfo)result;
                presentVoterInfoResult(voterInfo);
            }
        };

        // Callback for voterInfoQuery error result
        voterInfoErrorListener = new CivicInfoApiQuery.CallBackListener() {
            @Override
            public void callback(Object result) {
                try {
                    homeGoButton.setVisibility(View.INVISIBLE);
                    CivicApiError error = (CivicApiError) result;
                    Log.d("HomeFragment", "Civic API returned error");
                    Log.d("HomeFragment", error.code + ": " + error.message);
                    CivicApiError.Error error1 = error.errors.get(0);
                    Log.d("HomeFragment", error1.domain + " " + error1.reason + " " + error1.message);
                    if (CivicApiError.errorMessages.get(error1.reason) != null) {
                        homeTextViewStatus.setText(CivicApiError.errorMessages.get(error1.reason));
                    } else {
                        Log.d("HomeFragment", "Unknown API error reason: " + error1.reason);
                        homeTextViewStatus.setText(R.string.home_error_unknown);
                    }
                } catch(NullPointerException e) {
                    Log.e("HomeFragment", "Null encountered in API error result");
                    homeTextViewStatus.setText(R.string.home_error_unknown);
                }

                // read error result, if TalkBack enabled
                homeTextViewStatus.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
        };
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnInteractionListener {
        public void onGoButtonPressed(View view);
        public void onSelectContactButtonPressed(View view);
        public void searchedAddress(VoterInfo voterInfo);
    }

    public String getAddress() {
        // Bias the returned address towards a saved address in preferences if one does
        //  not exist in memory
        if (address == null || address.isEmpty()) {
            String addressKey = getString(R.string.LAST_ADDRESS_KEY);
            address = preferences.getString(addressKey, "");
        }
        return address;
    }

    /**
     * Store a new address into shared preferences, and clear out last saved election.
     * @param address Address string to store
     */
    public void setAddress(String address) {
        Log.d("HomeFragment", "Storing a new address into shared preferences.");
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear(); // clears last saved election, if there is one
        String addressKey = getString(R.string.LAST_ADDRESS_KEY);
        editor.putString(addressKey, address);
        editor.apply();
        this.address = address;
    }

    // Assumes that the currently selected election is the first in the list
    public void setSpinnerElections(List<Election> elections) {
        if (elections == null || elections.size() < 2) {
            homeElectionSpinnerWrapper.setVisibility(View.GONE);
            return;
        } else {
            homeElectionSpinnerWrapper.setVisibility(View.VISIBLE);
        }
        ArrayAdapter<Election> adapter = new ArrayAdapter<Election>(myActivity, R.layout.home_spinner_view, elections);
        homeElectionSpinner.setAdapter(adapter);
    }

    public void setSpinnerParty(List<Contest> contests) {
        HashSet<String> parties = new HashSet(5);
        if (contests != null) {
            for (Contest contest : contests) {
                // if contest has a primary party listed, it must be for a primary election
                if (contest.primaryParty != null && !contest.primaryParty.isEmpty()) {
                    parties.add(contest.primaryParty);
                }
            }
        } else {
            Log.d("HomeFragment", "No contests for election");
        }

        if (!parties.isEmpty()) {
            // convert set to list for adapter
            List<String> partiesList = new ArrayList<String>(parties);
            // sort list alphabetically
            Collections.sort(partiesList);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(myActivity, R.layout.home_spinner_view, partiesList);
            homePartySpinner.setAdapter(adapter);
            homePartySpinnerWrapper.setVisibility(View.VISIBLE);
        } else {
            homePartySpinnerWrapper.setVisibility(View.GONE);
        }
    }
}
