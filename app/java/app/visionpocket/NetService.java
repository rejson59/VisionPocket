package app.visionpocket;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tiny JSON-line TCP mesh for the shared room. One phone hosts (accepts),
 * others join; the host relays client messages to everyone, so the room is a
 * star. Every line is a JSON object terminated by '\n'.
 */
public class NetService {
    public static final int PORT = 47777;

    public interface Listener {
        /** Called on a net thread whenever the peer set changes. */
        void onPeersChanged();
        /** Called on a net thread for every non-control message. */
        void onMessage(JSONObject m);
        /** Human-readable status change (net thread). */
        void onStatus(String s);
    }

    public static class Peer {
        public final Socket socket;
        public volatile String name = "?";
        final PrintWriter out;
        Peer(Socket s, PrintWriter o) { socket = s; out = o; }
    }

    private final Object lock = new Object();
    private final List<Peer> peers = new ArrayList<>();
    private ServerSocket server;
    private Thread acceptThread;
    private Listener listener;
    private String selfName = "VP";

    public volatile boolean active = false;
    public volatile boolean isHost = false;
    private volatile String status = "";
    /** Latest peer-list payload broadcast by the host (for client UIs). */
    public volatile String roomList = "";

    public void setListener(Listener l) { listener = l; }
    public void setSelfName(String n) { selfName = n; }
    public String selfName() { return selfName; }

    public String status() { synchronized (lock) { return status; } }
    public List<Peer> snapshot() { synchronized (lock) { return new ArrayList<>(peers); } }

    public static String localIp() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    String h = a.getHostAddress();
                    if (a.isSiteLocalAddress() && h != null && h.indexOf(':') < 0) return h;
                }
            }
        } catch (Exception ignored) {}
        return "?";
    }

    public void startHost() {
        stop();
        isHost = true;
        active = true;
        setStatus("host");
        acceptThread = new Thread(this::acceptLoop, "vp-net-accept");
        acceptThread.start();
    }

    public void join(String ip) {
        stop();
        isHost = false;
        active = true;
        setStatus("connecting");
        fireStatus();
        final String addr = ip == null ? "" : ip.trim();
        new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new java.net.InetSocketAddress(addr, PORT), 3500);
                s.setSoTimeout(30000);
                addPeer(s);
                setStatus("connected " + addr);
                fireStatus();
            } catch (Exception e) {
                active = false;
                setStatus("fail");
                fireStatus();
            }
        }, "vp-net-join").start();
    }

    public void stop() {
        active = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        server = null;
        synchronized (lock) {
            for (Peer p : peers) try { p.socket.close(); } catch (Exception ignored) {}
            peers.clear();
        }
        roomList = "";
        if (status != null && !status.equals("off")) {
            status = "off";
            firePeers();
            fireStatus();
        }
    }

    private void acceptLoop() {
        try {
            server = new ServerSocket(PORT);
            while (active) {
                Socket s = server.accept();
                s.setSoTimeout(30000);
                addPeer(s);
            }
        } catch (Exception ignored) {
            // socket closed by stop()
        }
    }

    private void addPeer(Socket s) {
        PrintWriter out = null;
        try { out = new PrintWriter(s.getOutputStream(), true); } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) {}
            return;
        }
        Peer p = new Peer(s, out);
        synchronized (lock) { peers.add(p); }
        new Thread(() -> readLoop(p), "vp-net-peer").start();
        // greet so both sides learn the name
        sendTo(p, greeting());
        firePeers();
    }

    private String greeting() {
        try { return new JSONObject().put("t", "hi").put("n", selfName).toString(); }
        catch (Exception e) { return "{\"t\":\"hi\"}"; }
    }

    private void readLoop(Peer p) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(p.socket.getInputStream()));
            String line;
            while (active && (line = in.readLine()) != null) {
                JSONObject m;
                try { m = new JSONObject(line); } catch (Exception e) { continue; }
                String t = m.optString("t");
                if ("hi".equals(t)) {
                    String n = m.optString("n", "?");
                    if (n.length() > 24) n = n.substring(0, 24);
                    p.name = n.isEmpty() ? "?" : n;
                    if (isHost) broadcastRoomList();
                    firePeers();
                    continue;
                }
                if ("P".equals(t)) { roomList = m.optString("n", ""); firePeers(); continue; }
                if (listener != null) listener.onMessage(m);
                if (isHost) relay(p, m);   // star topology: host relays to others
            }
        } catch (Exception ignored) {
        } finally {
            drop(p);
        }
    }

    private void relay(Peer from, JSONObject m) {
        String json = m.toString();
        synchronized (lock) {
            for (Peer p : peers) if (p != from) sendTo(p, json);
        }
    }

    private void drop(Peer p) {
        boolean removed = false;
        synchronized (lock) {
            removed = peers.remove(p);
            try { p.socket.close(); } catch (Exception ignored) {}
        }
        if (removed) {
            if (isHost) broadcastRoomList();
            firePeers();
        }
    }

    /** Sends to every peer. Safe from any thread. */
    public void send(JSONObject m) {
        String json = m.toString();
        synchronized (lock) {
            for (Peer p : peers) sendTo(p, json);
        }
    }

    private void sendTo(Peer p, String json) {
        try { synchronized (p.out) { p.out.println(json); } }
        catch (Exception e) { drop(p); }
    }

    private void broadcastRoomList() {
        StringBuilder names = new StringBuilder();
        synchronized (lock) {
            for (Peer p : peers) {
                if (names.length() > 0) names.append(", ");
                names.append(p.name);
            }
        }
        try {
            send(new JSONObject().put("t", "P").put("n", names.toString()));
        } catch (Exception ignored) {}
    }

    private void setStatus(String s) { synchronized (lock) { status = s; } }
    private void firePeers() { if (listener != null) listener.onPeersChanged(); }
    private void fireStatus() { if (listener != null) listener.onStatus(status()); }
}
