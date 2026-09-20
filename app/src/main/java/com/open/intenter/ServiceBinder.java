package com.open.intenter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of services bound from the builder so they can be inspected
 * and unbound later. Bindings use the application context so they outlive
 * the fragment that created them.
 */
public final class ServiceBinder {

    public static final class Binding {
        public final long id;
        public final Intent intent;
        public final ServiceConnection connection;
        public boolean connected;
        public String descriptor = "";
        public ComponentName component;

        Binding(long id, Intent intent, ServiceConnection connection) {
            this.id = id;
            this.intent = intent;
            this.connection = connection;
        }

        public String label() {
            if (component != null) return component.flattenToShortString();
            return IntentDumper.summary(intent);
        }
    }

    public interface Listener {
        void onBindingsChanged();
    }

    private static ServiceBinder instance;
    private final Context app;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    private ServiceBinder(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized ServiceBinder get(Context context) {
        if (instance == null) instance = new ServiceBinder(context);
        return instance;
    }

    public List<Binding> bindings() {
        return new ArrayList<>(bindings);
    }

    public int count() { return bindings.size(); }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onBindingsChanged();
    }

    /** Binds and returns the binding; throws if bindService() returned false or threw. */
    public Binding bind(Intent intent) {
        final long id = System.nanoTime();
        final EventStore store = EventStore.get(app);
        final Binding[] holder = new Binding[1];
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Binding b = holder[0];
                String desc = "";
                try {
                    desc = service == null ? "(null binder)" : String.valueOf(service.getInterfaceDescriptor());
                } catch (RemoteException e) {
                    desc = "(remote error: " + e.getMessage() + ")";
                }
                if (b != null) {
                    b.connected = true;
                    b.descriptor = desc;
                    b.component = name;
                }
                boolean alive = service != null && service.isBinderAlive();
                store.add(EventStore.Kind.SERVICE, "Service connected",
                        name == null ? "" : name.flattenToShortString(),
                        "Component: " + (name == null ? "?" : name.flattenToString())
                                + "\nInterface descriptor: " + desc
                                + "\nBinder alive: " + alive
                                + "\nBinder class: " + (service == null ? "null" : service.getClass().getName())
                                + "\n\nBound intent:\n" + IntentDumper.dump(intent));
                notifyChanged();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Binding b = holder[0];
                if (b != null) b.connected = false;
                store.add(EventStore.Kind.SERVICE, "Service disconnected",
                        name == null ? "" : name.flattenToShortString(), "The service process died or was unbound.");
                notifyChanged();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                store.add(EventStore.Kind.SERVICE, "Binding died",
                        name == null ? "" : name.flattenToShortString(), "");
                unbind(id);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                Binding b = holder[0];
                if (b != null) { b.connected = true; b.descriptor = "(null binding)"; b.component = name; }
                store.add(EventStore.Kind.SERVICE, "Null binding",
                        name == null ? "" : name.flattenToShortString(),
                        "onBind() returned null: the service ran but exposes no binder.");
                notifyChanged();
            }
        };
        Binding binding = new Binding(id, intent, conn);
        holder[0] = binding;
        boolean ok = app.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        if (!ok) {
            try { app.unbindService(conn); } catch (RuntimeException ignored) {}
            throw new IllegalStateException("bindService() returned false: no matching service, or it is not exported / permission denied");
        }
        bindings.add(binding);
        notifyChanged();
        return binding;
    }

    public void unbind(long id) {
        for (int i = 0; i < bindings.size(); i++) {
            Binding b = bindings.get(i);
            if (b.id == id) {
                try { app.unbindService(b.connection); } catch (RuntimeException ignored) {}
                bindings.remove(i);
                EventStore.get(app).add(EventStore.Kind.SERVICE, "Unbound", b.label(), "");
                notifyChanged();
                return;
            }
        }
    }

    public void unbindAll() {
        for (Binding b : new ArrayList<>(bindings)) unbind(b.id);
    }
}
