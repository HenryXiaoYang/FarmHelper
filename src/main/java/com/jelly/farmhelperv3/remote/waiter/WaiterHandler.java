package com.jelly.farmhelperv3.remote.waiter;

import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.util.LogUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class WaiterHandler {
    private static final HashMap<Waiter, Integer> waiterMap = new HashMap<>();

    public static void register(Waiter waiterToRegister) {
        waiterMap.put(waiterToRegister, waiterToRegister.getTimeout());
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Pair<Waiter, Integer> waiterTuple = null;
                for (Waiter waiter : waiterMap.keySet()) {
                    if (waiter == waiterToRegister) {
                        waiterTuple = Pair.of(waiter, waiterMap.get(waiter));
                    }
                }
                if (waiterTuple != null && waiterTuple.getRight() != null && !waiterTuple.getLeft().isAnsweredAtLeastOnce()) { //if waiter was in the list && there is a timeoutAction
                    waiterToRegister.getTimeoutAction().accept(null);
                } else if (waiterTuple != null && waiterTuple.getLeft().isAnsweredAtLeastOnce()) {
                    waiterMap.remove(waiterTuple.getLeft());
                }
            }
        }, waiterToRegister.getTimeout());
    }

    public static void onMessage(RemoteMessage websocketMessage) {
        String command = websocketMessage.command;
        LogUtils.sendDebug("Received message: " + command);
        Set<Waiter> waiters = waiterMap.keySet();
        for (Waiter waiter : waiters) {
            if (waiter.getCommand().equalsIgnoreCase(command)) {
                waiter.getAction().accept(websocketMessage);
                waiter.setAnsweredAtLeastOnce(true);
                return;
            }
        }
        LogUtils.sendDebug("No waiter found for command: " + command);
    }
}
