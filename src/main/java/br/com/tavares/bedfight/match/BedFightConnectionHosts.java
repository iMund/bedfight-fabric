package br.com.tavares.bedfight.match;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.Connection;

/** Holds the hostname/port each connection's client used to connect (captured at handshake, see ServerHandshakeHostCaptureMixin) - keyed by the Connection object itself, which lives from handshake through the whole session. */
public final class BedFightConnectionHosts {
	private static final Map<Connection, HostInfo> HOSTS = Collections.synchronizedMap(new WeakHashMap<>());

	private BedFightConnectionHosts() {
	}

	public static void record(Connection connection, String hostName, int port) {
		if (connection == null) {
			return;
		}
		HOSTS.put(connection, new HostInfo(sanitize(hostName), port));
	}

	public static HostInfo get(Connection connection) {
		return connection == null ? null : HOSTS.get(connection);
	}

	private static String sanitize(String hostName) {
		if (hostName == null) {
			return "";
		}
		// Legacy (BungeeCord-style) forwarding embeds ip/uuid after a null character in the hostname.
		int nullIndex = hostName.indexOf('\0');
		String base = nullIndex >= 0 ? hostName.substring(0, nullIndex) : hostName;
		return base.trim();
	}

	public record HostInfo(String hostName, int port) {
	}
}
