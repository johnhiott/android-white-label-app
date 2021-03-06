package com.votinginfoproject.VotingInformationProject.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import com.votinginfoproject.VotingInformationProject.R;
import com.votinginfoproject.VotingInformationProject.activities.VIPTabBarActivity;
import com.votinginfoproject.VotingInformationProject.adapters.ContestsAdapter;
import com.votinginfoproject.VotingInformationProject.models.VoterInfo;


public class BallotFragment extends Fragment {

    VoterInfo voterInfo;
    VIPTabBarActivity myActivity;

    public static BallotFragment newInstance() {
        return new BallotFragment();
    }

    public BallotFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_ballot, container, false);
        myActivity = (VIPTabBarActivity)this.getActivity();
        // election label
        TextView election_name_label = (TextView)rootView.findViewById(R.id.ballot_election_name);
        TextView election_date_label = (TextView)rootView.findViewById(R.id.ballot_election_date);
        election_name_label.setText(voterInfo.election.name);
        election_date_label.setText(voterInfo.election.getFormattedDate());

        // fill list of contests
        ContestsAdapter adapter = new ContestsAdapter(myActivity, voterInfo.getFilteredContests(), voterInfo.election.name);
        adapter.sortList();
        ListView contestList = (ListView) rootView.findViewById(R.id.ballot_contests_list);

        // add footer view for feedback
        View feedback_layout = myActivity.getLayoutInflater().inflate(R.layout.feedback_link, contestList, false);
        // 'false' argument here is to make the footer list item not clickable (text instead is clickable)
        contestList.addFooterView(feedback_layout, null, false);

        contestList.setAdapter(adapter);
        contestList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                myActivity.showContestDetails(position);
            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        myActivity = (VIPTabBarActivity)this.getActivity();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // get election info
        voterInfo = ((VIPTabBarActivity) activity).getVoterInfo();
        Log.d("BallotFragment", "Got election: " + voterInfo.election.name);
    }
}
