import java.io.*;
import java.net.*;

import com.alipay.api.AlipayApiException;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

public class Server {
	private HierarchicalINIConfiguration config;
	private DefaultAlipayClient alipay_client;
	private AlipayTradePrecreateRequest alipay_pre_trade_req;
	
	void initAlipayClient() throws ConfigurationException {
		// 加载配置：
		config = new HierarchicalINIConfiguration("ydz.ini");
		String AliAppid, AliGateWay, ALIPAY_PUBLIC_KEY, APP_PRIVATE_KEY, 
			charset = "", 
			encode = "";
		AliAppid = config.getString("appid");
		ALIPAY_PUBLIC_KEY = config.getString("alipay_publick_key");
		APP_PRIVATE_KEY = config.getString("app_private_key");
		AliGateWay = config.getString("ali_gate_way");
		if (nullString(AliGateWay) || 
			nullString(ALIPAY_PUBLIC_KEY) ||
			nullString(APP_PRIVATE_KEY) ||
			nullString(AliGateWay)) {
			System.err.println("缺少必须的配置");
			System.exit(-1);
		}
		
		charset = config.getString("charset");
		encode = config.getString("encode");
		if (nullString(charset)) {
			charset = "utf-8";
		}
		if (nullString(encode)) {
			encode = "json";
		}
		// 实例化客户端
		alipay_client = new DefaultAlipayClient(AliGateWay, AliAppid, APP_PRIVATE_KEY,
				encode, charset, ALIPAY_PUBLIC_KEY);
		alipay_pre_trade_req = new AlipayTradePrecreateRequest();// 创建API对应的request类
	}

	String tradePreOrder(String no, float price, String operator) {
		ParamsMap param = new ParamsMap();
		// 参数具体含义ref:
		// https://doc.open.alipay.com/docs/api.htm?spm=a219a.7386797.0.0.2cPt2c&docType=4&apiId=862
		param.addKV("out_trade_no", no)
			.addKV("total_amount", price)
			.addKV("subject", "LESS & MORE 门店当面付消费")
			.addKV("operator_id", operator)
			.addKV("timeout_express", "30m")
			.addKV("body", "多少男装 风度担当");

		System.out.println("param json:\n" + param.toJson());
		alipay_pre_trade_req.setBizContent(param.toJson());
		try {
			AlipayTradePrecreateResponse response = alipay_client.execute(alipay_pre_trade_req);
			// 调用成功，则处理业务逻辑
			if (response.isSuccess()) {
				System.out.println("qr code url:" + response.getQrCode());
				return response.getQrCode();
				// 当面付异步通知：https://doc.open.alipay.com/doc2/detail.htm?treeId=194&articleId=103296&docType=1#s5
			}
		} catch (AlipayApiException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		return "";
	}

	void waitOrderStatus(String no, String operator) throws InterruptedException {
		AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();

		ParamsMap p = new ParamsMap();
		p.addKV("out_trade_no", no);
		req.setBizContent(p.toJson());

		AlipayTradeQueryResponse resp;
		try {
			resp = alipay_client.execute(req);
			String s = resp.getTradeStatus();
			String recall_url = config.getString("recall_url");
			int ti = 0, MAX_TRY = 0, loop_period = 0;
			MAX_TRY = config.getInt("loop_max_try");
			loop_period = config.getInt("loop_period");
			
			if (MAX_TRY == 0) {
				MAX_TRY = 900;
			}
			if (loop_period == 0) {
				loop_period = 2;
			}
			System.out.printf("waiting for pay status ... [%d][%s]\n", ti, s);
			// 当用户没有扫描的时候，s == null，显示没有交易记录
			while ((s == null || s.equals("WAIT_BUYER_PAY")) && ti < MAX_TRY) {
				resp = alipay_client.execute(req);
				s = resp.getTradeStatus();
				Thread.sleep(loop_period * 1000);
				ti++;
				System.out.printf("waiting for pay status ... [%d][%s]\n", ti, s);
			}

			// 调用http请求，发送结果
			if (nullString(recall_url)) {
				System.err.println("没有指定回调url！");
				return;
			}
			ParamsMap post_body = new ParamsMap();
			post_body.addKV("Opr", operator);
			if (ti >= MAX_TRY) {
				post_body.addKV("To", 1);
			} else {
				post_body.addKV("To", 0)
					.addKV("User", resp.getBuyerLogonId())
					.addKV("Total", resp.getTotalAmount());
				if (s == null) {
					post_body.addKV("St", -1)
					.addKV("Msg", "未知错误");
				} else if (s.equals("TRADE_SUCCESS")) {
					post_body.addKV("St", 0);
				} else if(s.equals("TRADE_CLOSED")) {
					post_body.addKV("St", 1)
						.addKV("Msg","交易超时关闭，或已全额退款");
				} else if(s.equals("TRADE_FINISHED")) {
					post_body.addKV("St", 1)
						.addKV("Msg", "交易结束");
				} else {
					post_body.addKV("St", -1)
						.addKV("Msg", "未知错误");
				}
			}
			executePost(recall_url, post_body.toUrlQuery());
		} catch (AlipayApiException e) {
			System.out.println(e.getErrMsg());
			e.printStackTrace();
		}
	}

	static void executePost(String targetURL, String urlParameters) {
		HttpURLConnection connection = null;

		try {
			// Create connection
			URL url = new URL(targetURL);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded");

			connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
		    connection.setRequestProperty("Content-Language", "en-US");  

			connection.setUseCaches(false);
			connection.setDoOutput(true);
			connection.setDoInput(true);
			// Send request
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.close();
			
			//Get Response  
		    InputStream is = connection.getInputStream();
		    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		    StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
		    String line;
		    while ((line = rd.readLine()) != null) {
		      response.append(line);
		      response.append("\n\r");
		    }
		    rd.close();
		    System.out.println("微信通知: " + response.toString());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
	public static void main(String[] args) 
			throws IOException, InterruptedException {
		ServerSocket listener = new ServerSocket(8686);
		Server myserver = new Server();
		try {
			myserver.initAlipayClient();
			while (true) {
				System.out.println("init a new thread...");
				new worker(listener.accept(), myserver).start();
			}
		} catch (ConfigurationException e) {
			System.err.println("init failed:\n" + e.getMessage());
			e.printStackTrace();
		}finally {
			listener.close();
		}
	}
	private	boolean nullString(String str) {
		return (str == null || str.equals(""));
	}
}

class worker extends Thread {
	private Socket sk;
	private Server s;

	worker(Socket sk, Server s) {
		super();
		this.sk = sk;
		this.s = s;
	}

	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(sk.getInputStream()));
			// 由其他程序发送过来的message必须以\n或\r结尾
			String input = in.readLine();
			PrintWriter out = new PrintWriter(sk.getOutputStream(), true);

			String seg[] = input.split(":");
			String tradeNo = seg[0];
			float price = Float.parseFloat(seg[1]);
			String operator = seg[2];
			String qr_url = s.tradePreOrder(tradeNo, price, operator);
			out.println("QR:"+qr_url);
			out.flush();
			if (!qr_url.equals("")) {
				Thread.sleep(20000);
				s.waitOrderStatus(tradeNo, operator);
			}
			sk.close();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
