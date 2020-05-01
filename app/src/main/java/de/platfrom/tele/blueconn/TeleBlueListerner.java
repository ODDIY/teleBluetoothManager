package de.platfrom.tele.blueconn;

public interface TeleBlueListerner {

    void onDistanceMessage(TeleCommunication.DistancesMessage msg);
    void onCollisionMessage(TeleCommunication.CollisionMessage msg);
    // void onStatusMessage(TeleCommunication.StatusMessage msg);
    //void onControlMessage(TeleCommunication.ControlMessage msg);
}
