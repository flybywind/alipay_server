import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.net.URLEncoder;


public class ParamsMap {
	Map<String, Object> m = new HashMap<String, Object>();
	public ParamsMap addKV(String key, Object value) {
		m.put(key, value);
		return this;
	}
	
	public String toJson() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for (Map.Entry<String, Object> en : m.entrySet()) {
			Object value = en.getValue();
			if (value instanceof String) {
				sb.append("\"" + en.getKey() + "\":" + "\"" + value.toString() + "\",");
			}else {
				sb.append("\"" + en.getKey() + "\":" + value.toString() + ",");
			}
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append("}");
		return sb.toString();
	}
	@SuppressWarnings("deprecation")
	public String toUrlQuery() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Object> en : m.entrySet()) {
			Object value = en.getValue();
			if (value == null) {
				value = "null";
			}
			sb.append(en.getKey() + "=" + URLEncoder.encode(value.toString()) + "&");
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
}
