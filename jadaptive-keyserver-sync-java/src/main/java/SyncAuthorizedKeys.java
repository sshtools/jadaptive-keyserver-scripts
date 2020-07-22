import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class SyncAuthorizedKeys {

	public static void main(String[] args) {
		
		String url = "https://gateway.jadaptive.com/";
		if(args.length > 0) {
			url = args[0];
			if(!url.endsWith("/")) {
				url += "/";
			}
		}
		
		url += "authorizedKeys/";
		
		System.out.println("Using authentication gateway URL " + url);
		
		File homeDir = new File(System.getProperty("user.home"));
		File sshDir = new File(homeDir, ".ssh");
		File authorizedUsers = new File(sshDir, "authorized_users");
		File staticKeys = new File(sshDir, "static_keys");
		File authorizedKeys = new File(sshDir, "authorized_keys");
		
		if(!authorizedUsers.exists()) {
			System.err.println("No ~/.ssh/authorized_users file to synchronize keys from");
			System.exit(1);
		}
		
		System.out.println("Checking file permissions");
		
		try {
			checkPermissions(homeDir, true);
			checkPermissions(sshDir, true);
			checkPermissions(authorizedUsers, true);
			checkPermissions(staticKeys, false);
			checkPermissions(authorizedKeys, false);
		} catch (PermissionDeniedException e) {
			System.err.println("Fatal error: " + e.getMessage());
			System.exit(1);
		}

		System.out.println("Building authorized_keys");
		
		StringBuffer keys = new StringBuffer();
		
		try(InputStream in = new FileInputStream(authorizedUsers)) {
		
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String user;
			while((user = reader.readLine())!=null) {
				if(!user.startsWith("#")) {
					synchronizeKeys(url, user, keys);
				}
			}
		
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		if(staticKeys.exists()) {
			
			System.out.println("Adding static keys from ~/.ssh/static_keys");
			
			try(InputStream in = new FileInputStream(staticKeys)) {
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String key;
				while((key = reader.readLine())!=null) {
					if(!key.startsWith("#")) {
						keys.append(key);
						keys.append(System.lineSeparator());
					}
				}
			
			} catch(IOException ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		}
		
		System.out.println("Writing authorized_keys");
		try (OutputStream out = new FileOutputStream(authorizedKeys)) {
			out.write(keys.toString().getBytes("UTF-8"));
			out.flush();
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

	private static void synchronizeKeys(String url, String user, StringBuffer keys) throws IOException {

		System.out.println("Synchronizing keys for user " + user);
		
		URL u = new URL(url + user);
		try(InputStream in = u.openConnection().getInputStream()) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String key;
			while((key = reader.readLine())!=null) {
				if(!key.startsWith("#")) {
					keys.append(key);
					keys.append(System.lineSeparator());
				}
			}
		}
	}

	private static void checkPermissions(File file, boolean require) throws PermissionDeniedException {
		
		try {
			PosixFileAttributes attrs = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
			Set<PosixFilePermission> perms = attrs.permissions();
			if(perms.contains(PosixFilePermission.GROUP_WRITE) || perms.contains(PosixFilePermission.OTHERS_WRITE)) {
				throw new PermissionDeniedException("File permissions too open for " + file.getName());
			}
		} catch (UnsupportedOperationException | IOException e) {
		}
		
	}
}
