package com.disabledtech.winremote.view;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.disabledtech.winremote.R;
import com.disabledtech.winremote.control.BTConnectionClient;
import com.disabledtech.winremote.control.BTDataIO;
import com.disabledtech.winremote.exceptions.ServerConnectionClosedException;
import com.disabledtech.winremote.interfaces.IServerConnectionListener;
import com.disabledtech.winremote.model.WinAction;
import com.disabledtech.winremote.model.WinActionButton;
import com.disabledtech.winremote.utils.Debug;
import com.disabledtech.winremote.utils.Device;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, IServerConnectionListener, View.OnClickListener
{

	private static final int REQUEST_ACCESS_COARSE_LOCATION = 1; // used to identify permission requests

	private BTConnectionClient m_ConnectionClient;
	private BTDataIO m_DataIO;
	private WinActionViewAdapter m_ActionViewAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		initializeLayout();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		m_DataIO.closeConnection();
	}

	@Override
	public void onClick(View view)
	{
		if (view instanceof WinActionButton)
		{
			handleWinButtonPressed((WinActionButton) view);
			return;
		}

		Debug.logError("UI view click not implemented!");
	}

	private void handleWinButtonPressed(WinActionButton buttonPressed)
	{
		try
		{
			m_DataIO.send(buttonPressed.getWinAction());
		}
		catch (ServerConnectionClosedException scce)
		{
			// TODO better UI handling
			Device.showToast(this, "Server connection lost.");
			clearButtons();
		}
		catch (IOException ioe)
		{
			// TODO UI feedback
			Debug.logError("Error sending button information to server.");
			ioe.printStackTrace();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();

		switch (id)
		{
			case R.id.action_connect:
				// TODO user feedback while connection to server is being established, have this perform automatically on launch
				Device.showToast(this, "Attempting connection...");
				closeServerConnection();
				beginServerConnection();
				return true;

			case R.id.action_disconnect:
				closeServerConnection();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Closes the connection, sets m_DataIO to null and
	 * clears all buttons from the screen.
	 */
	private void closeServerConnection()
	{
		if (m_DataIO == null) return;

		m_DataIO.closeConnection();
		m_DataIO = null;
		clearButtons();
	}

	/**
	 * Clears all the buttons from the grid layout.
	 */
	private void clearButtons()
	{
		m_ActionViewAdapter.removeAllActions();
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item)
	{
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		switch (id)
		{
			// TODO templates
		}

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);

		return true;
	}

	@Override
	public void onBackPressed()
	{
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

		// if the template drawer is open, back should just close it
		if (drawer.isDrawerOpen(GravityCompat.START))
		{

			drawer.closeDrawer(GravityCompat.START);
			return;
		}

		super.onBackPressed();
	}

	/**
	 * Requests bluetooth connection permissions if applicable,
	 * and then starts the server connection process.
	 */
	private void beginServerConnection()
	{
		if (Device.hasBluetoothPermissions(this))
		{
			getServerConnection();
			return;
		}

		// TODO display reason for coarse location (needs it to discover BT devices)
		ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
				REQUEST_ACCESS_COARSE_LOCATION);
	}

	/**
	 * Initializes a {@link BTConnectionClient} and begins the connection
	 * process.
	 */
	public void getServerConnection()
	{
		if (m_ConnectionClient == null)
		{
			m_ConnectionClient = new BTConnectionClient(this, this);
		}

		m_ConnectionClient.attemptBondedConnection();
	}

	/**
	 * Returned after the request permission dialog is given. If permissions
	 * were successful, then a server connection will attempt to be established.
	 *
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		boolean permissionGranted = grantResults.length > 0 &&
				grantResults[0] == PackageManager.PERMISSION_GRANTED;

		if (permissionGranted == false)
		{
			return; // TODO handle refused connection (prompt and close, likely.) update docs.
		}

		switch (requestCode)
		{
			case REQUEST_ACCESS_COARSE_LOCATION:

				getServerConnection();
				break;
		}
	}

	@Override
	public void serverConnected(BluetoothSocket connectedSocket)
	{
		Device.showToast(this, "Connection to the server made!");
		m_DataIO = new BTDataIO(connectedSocket);

		try
		{
			List<WinAction> userActions = m_DataIO.getActionData();
			populateActivityButtons(userActions);
		} catch (IOException ioe)
		{
			Debug.log(ioe.getMessage());
			Device.showToast(this, "Failure reading buttons from server!");
		}
	}

	/**
	 * Takes the given list of WinActions and populates the UI based on
	 * those actions.
	 *
	 * @param userActions
	 */
	private void populateActivityButtons(List<WinAction> userActions)
	{
		if (userActions.size() == 0)
		{
			Device.showToast(this, "Server has no buttons defined.");
		}

		GridView layout = (GridView) findViewById(R.id.grid_layout);
		this.m_ActionViewAdapter = new WinActionViewAdapter(this, this, userActions);
		layout.setAdapter(m_ActionViewAdapter);
	}

	@Override
	public void notifyCriticalFailure(SERVER_ERROR_CODE error)
	{
		// TODO handle communication with user
		Device.showToast(this, "Connection failure " + error);
	}

	@Override
	public void notifyRecoverableFailure(SERVER_ERROR_CODE error)
	{
		// TODO handle communication with user
		Device.showToast(this, "Connection failure " + error);
	}

	private void initializeLayout()
	{
		setContentView(R.layout.activity_main);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.setDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_interface, menu);
		return true;
	}
}
