package com.repay.android;

import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.database.CursorIndexOutOfBoundsException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.repay.android.database.DatabaseHandler;
import com.repay.android.model.Debt;
import com.repay.android.model.Friend;
import com.repay.android.settings.SettingsFragment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Property of Matt Allen
 * mattallen092@gmail.com
 * http://mattallensoftware.co.uk/
 * <p/>
 * This software is distributed under the Apache v2.0 license and use
 * of the Repay name may not be used without explicit permission from the project owner.
 */

public class StartFragment extends Fragment
{

	public static final String TAG = StartFragment.class.getName();

	private RecyclerView mList;
	private TextView mEmptyState;
	private FriendListAdapter mAdapter;
	private ProgressBar mProgressBar;
	private int mListItem = R.layout.friend_grid_item, mSortOrder;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_start, container, false);
		mList = (RecyclerView)view.findViewById(R.id.list);
		mEmptyState = (TextView)view.findViewById(R.id.empty);
		mProgressBar = (ProgressBar)view.findViewById(R.id.progress);
		mProgressBar.setVisibility(ProgressBar.GONE);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		mList.setHasFixedSize(true);

		mList.setLayoutManager(new StaggeredGridLayoutManager(
			getResources().getInteger(R.integer.mainactivity_cols),
			StaggeredGridLayoutManager.VERTICAL)
		);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		updateList();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		super.onOptionsItemSelected(item);
		switch (item.getItemId())
		{
			case R.id.action_total:
				showTotalDialog();
				return true;

			case R.id.action_recalculateTotals:
				new RecalculateTotalDebts().execute(((MainActivity)getActivity()).getDB());

			default:
				return true;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true); // Tell the activity that we have ActionBar items
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inf)
	{
		super.onCreateOptionsMenu(menu, inf);
		inf.inflate(R.menu.friendslist, menu);
	}

	public void updateList()
	{
		if (((MainActivity)getActivity()).getFriends() == null || ((MainActivity)getActivity()).getFriends().size() == 0)
		{
			mList.setVisibility(View.GONE);
			mEmptyState.setVisibility(View.VISIBLE);
			mProgressBar.setVisibility(ProgressBar.GONE);
		}
		else
		{
			mList.setVisibility(View.VISIBLE);
			mEmptyState.setVisibility(View.GONE);
			mProgressBar.setVisibility(ProgressBar.GONE);

			mAdapter = new FriendListAdapter(getActivity(), ((MainActivity)getActivity()).getFriends(), FriendListAdapter.VIEW_GRID);
			mList.setAdapter(mAdapter);
		}
	}

	private BigDecimal calculateTotalDebt()
	{
		if (((MainActivity)getActivity()).getFriends() != null)
		{
			BigDecimal total = new BigDecimal("0");
			for (Friend friend : ((MainActivity)getActivity()).getFriends())
			{
				total = total.add(friend.getDebt());
			}
			return total;
		}
		else
		{
			return new BigDecimal("0");
		}
	}

	public void showTotalDialog()
	{
		View v = LayoutInflater.from(getActivity()).inflate(R.layout.total_dialog, null, false);
		BigDecimal totalAmount = calculateTotalDebt();

		if (totalAmount.compareTo(BigDecimal.ZERO) == 1)
		{
			((TextView)v.findViewById(R.id.title)).setText(R.string.youre_owed);
			((TextView)v.findViewById(R.id.amount)).setText(SettingsFragment.getCurrencySymbol(getActivity()) + calculateTotalDebt().toString());
		}
		else if (totalAmount.compareTo(BigDecimal.ZERO) == 0)
		{
			((TextView)v.findViewById(R.id.title)).setText(R.string.even_debt);
			((TextView)v.findViewById(R.id.amount)).setText(SettingsFragment.getCurrencySymbol(getActivity()) + calculateTotalDebt().toString());
		}
		else if (totalAmount.compareTo(BigDecimal.ZERO) == -1)
		{
			((TextView)v.findViewById(R.id.title)).setText(R.string.i_owe);
			((TextView)v.findViewById(R.id.amount)).setText(SettingsFragment.getCurrencySymbol(getActivity()) + calculateTotalDebt().negate().toString());
		}

		new Builder(getActivity()).setView(v).setPositiveButton(R.string.close, null).show();
	}

	private class RecalculateTotalDebts extends AsyncTask<DatabaseHandler, Integer, ArrayList<Friend>>
	{

		@Override
		protected void onPreExecute()
		{
			mList.setAdapter(null);
			mList.setVisibility(ListView.GONE);
			mEmptyState.setVisibility(RelativeLayout.GONE);
			mProgressBar.setVisibility(ProgressBar.VISIBLE);
		}

		private BigDecimal totalAllDebts(ArrayList<Debt> debts)
		{
			BigDecimal amount = new BigDecimal("0");
			if (debts != null && debts.size() > 0)
			{
				for (int i = 0; i <= debts.size() - 1; i++)
				{
					amount = amount.add(debts.get(i).getAmount());
				}
			}
			return amount;
		}

		@Override
		protected ArrayList<Friend> doInBackground(DatabaseHandler... params)
		{
			try
			{
				ArrayList<Friend> friends = params[0].getAllFriends();
				if (friends != null)
				{
					for (int i = 0; i <= friends.size() - 1; i++)
					{
						BigDecimal newAmount;
						try
						{
							newAmount = totalAllDebts(params[0].getDebtsByRepayID(friends.get(i).getRepayID()));
						}
						catch (Exception e)
						{
							e.printStackTrace();
							newAmount = new BigDecimal("0");
						}
						try
						{
							friends.get(i).setDebt(newAmount);
							params[0].updateFriendRecord(friends.get(i));
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
					}

					Collections.sort(friends);
					if (mSortOrder == SettingsFragment.SORTORDER_OWETHEM)
					{
						Collections.reverse(friends);
					}
					return friends;
				}
			}
			catch (CursorIndexOutOfBoundsException e)
			{
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(ArrayList<Friend> result)
		{
			if (result != null && result.size() > 0)
			{
				mAdapter = new FriendListAdapter(getActivity(), result, FriendListAdapter.VIEW_GRID);
				mList.setVisibility(ListView.VISIBLE);
				mList.setAdapter(mAdapter);
			}
			else
			{
				mEmptyState.setVisibility(RelativeLayout.VISIBLE);
			}

			mProgressBar.setVisibility(ProgressBar.GONE);
		}
	}
}
