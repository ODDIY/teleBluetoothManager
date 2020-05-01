package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.app.AlertDialog;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.platfrom.tele.blueconn.TeleBlueConnection;
import de.platfrom.tele.blueconn.TeleCommunication;

public class TerminalFragment extends Fragment {

    private String newline = "\r\n";
    private TextView receiveText;


    private TeleBlueConnection conn = new TeleBlueConnection();

    /*
     * Lifecycle Setup connection service
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        String deviceAddress = getArguments().getString("device");
        conn.setDevice(deviceAddress);
    }

    @Override
    public void onDestroy() {
            conn.destroyActivity(getActivity());
          super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        conn.startActivity(getActivity(), getContext());
    }

    @Override
    public void onStop() {
        conn.stopActivity(getActivity());
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        conn.attachActivity(activity);
        //.getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        conn.detachActivity(getActivity());
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        conn.reconnect();
    }


    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);

        TextView sX = view.findViewById(R.id.speedX);
        TextView sY = view.findViewById(R.id.speedY);
        TextView sRot = view.findViewById(R.id.speedRot);

        sendBtn.setOnClickListener(v -> {
            try {
                int valX = Integer.parseInt(sX.getText().toString());
                int valY = Integer.parseInt(sY.getText().toString());
                int valRot = Integer.parseInt(sRot.getText().toString());
                TeleCommunication.ControlMessage.Builder builder = TeleCommunication.ControlMessage.newBuilder();
                builder.setSpeedX(valX);
                builder.setSpeedY(valY);
                builder.setSpeedRot(valRot);
                TeleCommunication.ControlMessage msg = builder.build();
                conn.sendControlMessage(msg);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

        });
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id ==R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

}
