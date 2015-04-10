package com.quickblox.sample.videochatwebrtcnew.activities;


import android.app.Fragment;
import android.app.FragmentManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBSignaling;
import com.quickblox.chat.QBWebRTCSignaling;
import com.quickblox.chat.listeners.QBVideoChatSignalingListener;
import com.quickblox.chat.listeners.QBVideoChatSignalingManagerListener;
import com.quickblox.sample.videochatwebrtcnew.ApplicationSingleton;
import com.quickblox.sample.videochatwebrtcnew.R;
import com.quickblox.sample.videochatwebrtcnew.adapters.OpponentsAdapter;
import com.quickblox.sample.videochatwebrtcnew.fragments.ConversationFragment;
import com.quickblox.sample.videochatwebrtcnew.fragments.IncomeCallFragment;
import com.quickblox.sample.videochatwebrtcnew.fragments.OpponentsFragment;
import com.quickblox.sample.videochatwebrtcnew.helper.DataHolder;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientConnectionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientVideoTracksCallbacks;
import com.quickblox.videochat.webrtc.view.QBGLVideoView;
import com.quickblox.videochat.webrtc.view.QBRTCVideoTrack;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Message;
import org.webrtc.PeerConnection;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by tereha on 16.02.15.
 */
public class CallActivity extends BaseLogginedUserActivity implements /*QBRTCClientCallback*/QBRTCClientConnectionCallbacks,QBRTCClientVideoTracksCallbacks,QBRTCClientSessionCallbacks {


    private static final String TAG = "CallActivity";
    public static final String OPPONENTS_CALL_FRAGMENT = "opponents_call_fragment";
    public static final String INCOME_CALL_FRAGMENT = "income_call_fragment";
    public static final String CONVERSATION_CALL_FRAGMENT = "conversation_call_fragment";
    public static final String CALLER_NAME = "caller_name";
    private static final java.lang.String ADD_OPPONENTS_FRAGMENT_HANDLER = "opponentHandlerTask";
    private static final long TIME_BEGORE_CLOSE_CONVERSATION_FRAGMENT = 3;
    private static final java.lang.String INCOME_WINDOW_SHOW_TASK = "INCOME_WINDOW_SHOW";
    private static VideoRenderer.Callbacks REMOTE_RENDERER;
    private static VideoRenderer.Callbacks LOCAL_RENDERER;


    public static final String START_CONVERSATION_REASON = "start_conversation_reason";
    public static final String SESSION_ID = "sessionID";
    private QBRTCVideoTrack localVideoTrack;
    private String currentSession;

    private QBGLVideoView videoView;
    public static String login;
    public static Map<Integer, QBRTCVideoTrack> videoTrackList = new HashMap<>();
    public static ArrayList<QBUser> opponentsList;
    private HandlerThread showIncomingCallWindowTaskThread;
    private Runnable showIncomingCallWindowTask;
    private Handler showIncomingCallWindowTaskHandler;
    private QBRTCSession session;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.d(TAG, "Activity. Thread id: " + Thread.currentThread().getId());

        // Probably initialize members with default values for a new instance
        login = getIntent().getStringExtra("login");

        if (savedInstanceState == null) {
            addOpponentsFragment();
        }

        Log.d("Track", "onCreate() from NewDialogActivity Level 1");
    }


    @Override
    protected void onStart() {
        super.onStart();
        // From hear we start listening income call
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                QBRTCClient.init(CallActivity.this);
            }
        });

        // Add signalling manager
//        QBRTCClient.getInstance().setSignalingManager(QBChatService.getInstance().getVideoChatWebRTCSignalingManager());
        initCallListaning();


        // Set custom ice servers up. Use it in case you want set your own servers instead of defaults
        List<PeerConnection.IceServer> iceServerList = new LinkedList<>();
        iceServerList.add(new PeerConnection.IceServer("turn:numb.viagenie.ca", "petrbubnov@grr.la", "petrbubnov@grr.la"));
        iceServerList.add(new PeerConnection.IceServer("turn:numb.viagenie.ca:3478?transport=udp", "petrbubnov@grr.la", "petrbubnov@grr.la"));
        iceServerList.add(new PeerConnection.IceServer("turn:numb.viagenie.ca:3478?transport=tcp", "petrbubnov@grr.la", "petrbubnov@grr.la"));
        QBRTCConfig.setIceServerList(iceServerList);

        // Add activity as callback to RTCClient
        if (QBRTCClient.isInitiated()) {
            QBRTCClient.getInstance().addConnectionCallbacksListener(this);
            QBRTCClient.getInstance().addSessionCallbacksListener(this);
            QBRTCClient.getInstance().addVideoTrackCallbacksListener(this);
        }
    }


    private void initCallListaning() {
        QBChatService.getInstance().getVideoChatWebRTCSignalingManager().addSignalingManagerListener(
                new QBVideoChatSignalingManagerListener() {
                    @Override
                    public void signalingCreated(QBSignaling signaling, boolean createdLocally) {
                        if (!createdLocally)
                            QBRTCClient.getInstance().addSignaling((QBWebRTCSignaling) signaling);
                    }
                });
    }

    public QBRTCSession getCurrentSession() {
        return session;
    }

    public void setCurrentSession(QBRTCSession session) {
        this.session = session;
        currentSession = session.getSessionID();
    }

    public void setCurrentSessionId(String sesionId) {
        this.currentSession = sesionId;
    }


//    public void setLocalRenderer(VideoRenderer.Callbacks localRenderer){
//        this.localRenderer = localRenderer;
//    }
//
//    public void setRemouteRenderer(List<VideoRenderer.Callbacks> remouteRenderers){
//        this.opponentRenderers = remouteRenderers;
//    }

    public void setVideoView(QBGLVideoView videoView) {
        this.videoView = videoView;
    }

    // ---------------Chat callback methods implementation  ----------------------//

    @Override
    public void onReceiveNewSession(QBRTCSession session) {
//        Toast.makeText(this, "IncomeCall", Toast.LENGTH_SHORT).show();

        if (currentSession == null) {
            Log.d(TAG, "Start new session");
            Log.d(TAG, "Income call");
//            QBRTCClient.getInstance().getSessions().put(session.getSessionID(), session);
            setCurrentSession(session);
            addIncomeCallFragment(session);
        } else {
            Log.d(TAG, "Stop new session. Device now is busy");
            session.rejectCall(null);
        }
    }

    @Override
    public void onUserNotAnswer(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.noAnswer, View.VISIBLE);
//        addOpponentsFragmentWithDelay();
    }

    @Override
    public void onStartConnectToUser(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.checking, View.VISIBLE);

        ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
        if (fragment != null) {
            fragment.actionButtonsEnabled(true);
        }
    }

    @Override
    public void onCallRejectByUser(QBRTCSession session, Integer userID, Map<String, String> userInfo) {
        setStateTitle(userID, R.string.rejected, View.INVISIBLE);
    }

    @Override
    public void onLocalVideoTrackReceive(QBRTCSession session, QBRTCVideoTrack videoTrack) {
        this.localVideoTrack = videoTrack;
        videoTrack.addRenderer(new VideoRenderer(LOCAL_RENDERER));
//        videoTrack.addRenderer(new VideoRenderer(new VideoCallBacks(videoView, QBGLVideoView.Endpoint.LOCAL)));
//        videoView.setVideoTrack(videoTrack, QBGLVideoView.Endpoint.LOCAL);
        Log.d("Track", "onLocalVideoTrackReceive() is raned");
    }

    @Override
    public void onRemoteVideoTrackReceive(QBRTCSession session, QBRTCVideoTrack videoTrack, Integer userID) {
//        VideoCallBacks videoCallBacks = new VideoCallBacks(videoView, QBGLVideoView.Endpoint.REMOTE);
//        videoCallBacks.setSize(200, 300);
//        VideoRenderer remouteRenderer = new VideoRenderer(videoCallBacks);
//        videoTrack.addRenderer(remouteRenderer);
        videoTrack.addRenderer(new VideoRenderer(REMOTE_RENDERER));
        videoTrackList.put(userID, videoTrack);
        Log.d("Track", "onRemoteVideoTrackReceive() is raned");
    }

    @Override
    public void onConnectionClosedForUser(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.closed, View.INVISIBLE);
    }

    @Override
    public void onConnectedToUser(QBRTCSession session, Integer userID) {
        startTimer();

        setStateTitle(userID, R.string.connected, View.INVISIBLE);

        Log.d("Track", "onConnectedToUser() is started");
    }

    @Override
    public void onDisconnectedTimeoutFromUser(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.time_out, View.INVISIBLE);
    }

    @Override
    public void onConnectionFailedWithUser(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.failed, View.INVISIBLE);
    }

    @Override
    public void onSessionClosed(QBRTCSession session) {
        if (session.getSessionID().equals(currentSession)) {
            Log.d(TAG, "Stop session");
            addOpponentsFragmentWithDelay();

            // Remove current session
            Log.d(TAG, "Remove current session");
            currentSession = null;
        }
    }

    @Override
    public void onSessionStartClose(QBRTCSession session) {

        ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
        if (fragment != null && session.equals(getCurrentSession())) {
            fragment.actionButtonsEnabled(false);
        }

        Log.d(TAG, "Start stopping session");
    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession session, Integer userID) {
        setStateTitle(userID, R.string.disconnected, View.INVISIBLE);
    }

    private void setStateTitle(Integer userID, int stringID, int progressBarVisibility) {
        Log.d(TAG, "Start Change opponent state");
        View opponentItemView = findViewById(userID);
        if (opponentItemView != null) {
            TextView connectionStatus = (TextView) opponentItemView.findViewById(R.id.connectionStatus);
            connectionStatus.setText(getString(stringID));

            ProgressBar connectionStatusPB = (ProgressBar) opponentItemView.findViewById(R.id.connectionStatusPB);
            connectionStatusPB.setVisibility(progressBarVisibility);
            Log.d(TAG, "Opponent state changed to " + getString(stringID));
        }
    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession session, Integer userID) {

        setStateTitle(userID, R.string.hungUp, View.INVISIBLE);
    }

    public void addOpponentsFragmentWithDelay() {
        HandlerThread handlerThread = new HandlerThread(ADD_OPPONENTS_FRAGMENT_HANDLER);
        handlerThread.start();
        new Handler(handlerThread.getLooper()).postAtTime(new Runnable() {
            @Override
            public void run() {
                    getFragmentManager().beginTransaction().replace(R.id.fragment_container, new OpponentsFragment(), OPPONENTS_CALL_FRAGMENT).commit();
                    currentSession = null;
            }
        }, SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIME_BEGORE_CLOSE_CONVERSATION_FRAGMENT));
    }

    public void addOpponentsFragment() {
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, new OpponentsFragment(), OPPONENTS_CALL_FRAGMENT).commit();
    }


    public void removeIncomeCallFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(INCOME_CALL_FRAGMENT);

        if (fragment != null) {
            fragmentManager.beginTransaction().remove(fragment).commit();
        }
    }


    private void addIncomeCallFragment(QBRTCSession session) {
        Fragment fragment = new IncomeCallFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable("sessionDescription", session.getSessionDescription());
        bundle.putIntegerArrayList("opponents", new ArrayList<Integer>(session.getOpponents()));
        bundle.putInt(ApplicationSingleton.CONFERENCE_TYPE, session.getConferenceType().getValue());
        fragment.setArguments(bundle);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, INCOME_CALL_FRAGMENT).commit();

    }


    public void addConversationFragmentStartCall(List<Integer> opponents,
                                                 QBRTCTypes.QBConferenceType qbConferenceType,
                                                 Map<String, String> userInfo) {

        // init session for new call
        try {
            QBRTCSession newSessionWithOpponents = QBRTCClient.getInstance().createNewSessionWithOpponents(opponents, qbConferenceType);
            setCurrentSession(newSessionWithOpponents);

            ConversationFragment fragment = new ConversationFragment();
            Bundle bundle = new Bundle();
            bundle.putIntegerArrayList(ApplicationSingleton.OPPONENTS,
                    new ArrayList<Integer>(opponents));
            bundle.putInt(ApplicationSingleton.CONFERENCE_TYPE, qbConferenceType.getValue());
            bundle.putInt(START_CONVERSATION_REASON, StartConversetionReason.OUTCOME_CALL_MADE.ordinal());
            bundle.putString(CALLER_NAME, DataHolder.getUserNameByID(opponents.get(0)));

            for (String key : userInfo.keySet()) {
                bundle.putString("UserInfo:" + key, userInfo.get(key));
            }
            fragment.setArguments(bundle);
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, CONVERSATION_CALL_FRAGMENT).commit();
        } catch (IllegalStateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    public void addConversationFragmentReceiveCall(String sessionID) {

        // set current session
        setCurrentSessionId(sessionID);
        QBRTCSession session = getCurrentSession();
        Integer myId = QBChatService.getInstance().getUser().getId();
        ArrayList<Integer> opponentsWithoutMe = new ArrayList<>(session.getOpponents());
        opponentsWithoutMe.remove(new Integer(myId));
        opponentsWithoutMe.add(session.getCallerID());

        // init conversation fragment
        ConversationFragment fragment = new ConversationFragment();
        Bundle bundle = new Bundle();
        bundle.putIntegerArrayList(ApplicationSingleton.OPPONENTS,
                opponentsWithoutMe);
        bundle.putInt(ApplicationSingleton.CONFERENCE_TYPE, session.getConferenceType().getValue());
        bundle.putInt(START_CONVERSATION_REASON, StartConversetionReason.INCOME_CALL_FOR_ACCEPTION.ordinal());
        bundle.putString(SESSION_ID, sessionID);
        bundle.putString(CALLER_NAME, DataHolder.getUserNameByID(session.getCallerID()));

        if (session.getUserInfo() != null) {
            for (String key : session.getUserInfo().keySet()) {
                bundle.putString("UserInfo:" + key, session.getUserInfo().get(key));
            }
        }
        fragment.setArguments(bundle);

        // Start conversation fragment
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, CONVERSATION_CALL_FRAGMENT).commit();
    }


    public void setCurrentVideoView(GLSurfaceView videoView) {
        VideoRendererGui.ScalingType scaleType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
//
        REMOTE_RENDERER = VideoRendererGui.create(0, 0, 100, 100, scaleType, true);
//
//        //next value in percentage of the available space
//        int marginLeft = 0;
//        int marginTop = 0;
//        int height = 0;
//        int width = 0;
//
//        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
//            marginLeft = 70;
//            marginTop = 0;
//            height = 30;
//            width = 22;
//        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
//            marginLeft = 69;
//            marginTop = 0;
//            height = 15;
//            width = 34;
//        }

        LOCAL_RENDERER = VideoRendererGui.create(70, 0, 30, 30, scaleType, true);
    }

    public void startTimer() {
        super.startTimer();
    }

    public void setOpponentsList(ArrayList<QBUser> qbUsers) {
        this.opponentsList = qbUsers;
    }

    public ArrayList<QBUser> getOpponentsList() {
        return opponentsList;
    }

    public static enum StartConversetionReason {
        INCOME_CALL_FOR_ACCEPTION,
        OUTCOME_CALL_MADE;
    }

    @Override
    public void onBackPressed() {
        // Logout on back btn click
        Fragment fragment = getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
        if (fragment == null) {
            super.onBackPressed();
            if (QBChatService.isInitialized()) {
                try {
                    QBRTCClient.getInstance().close();
                    QBChatService.getInstance().logout();
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        opponentsList = null;
        OpponentsAdapter.i = 0;
        // Remove activity as callback to RTCClient
//        if (QBRTCClient.isInitiated()) {
//            try {
//                QBChatService.getInstance().logout();
//            } catch (SmackException.NotConnectedException e) {
//                e.printStackTrace();
//            }
//            QBRTCClient.getInstance().removeCallback(this);
//            QBChatService.getInstance().destroy();
//        }
    }

}

