package http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class GetRandomUUID {


	public static String getuuid() throws MalformedURLException {
		String uid = "";
		URL url = new URL("http://www.randomnumberapi.com/api/v1.0/randomuuid");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
		    for (String line; (line = reader.readLine()) != null;) {
		        uid = line;
		    }
		} catch (Exception e) {
			uid = "ERROR: "+e.getCause();
		}
		
		return uid;
	}
}
