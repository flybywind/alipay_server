import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import com.alipay.api.AlipayApiException;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;


public class Server {
	static String AliAppid = "2016081801766057";
	static String ALIPAY_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDjWupEAleY/zi+l58Ld6WdGZmaV58jO8M8yrJ1MG8zV0ytcWb5ZkcmQZSUtWXfseRp0TgLr/ua+kMzW2PvR8GgjSCvVWYSA3gfq2fwp2GgS7UFSVBU9XHFWYV2peE8bf2zNiy/zoB3b5R631s1cz5LXyHIgiRAFcmt4Lr6XLsK2QIDAQAB";
	static String APP_PRIVATE_KEY = "MIICcwIBADANBgkqhkiG9w0BAQEFAASCAl0wggJZAgEAAoGBAONa6kQCV5j/OL6Xnwt3pZ0ZmZpXnyM7wzzKsnUwbzNXTK1xZvlmRyZBlJS1Zd+x5GnROAuv+5r6QzNbY+9HwaCNIK9VZhIDeB+rZ/CnYaBLtQVJUFT1ccVZhXal4Txt/bM2LL/OgHdvlHrfWzVzPktfIciCJEAVya3guvpcuwrZAgMBAAECfyYwHylNO2l3dRCOZyiF8EtzAVnrXc+NOj37zf3hJMx63WZEpgc+JrVGTq6ryXDJcJRVkBRmetyNLLxznVWTt/Huls5p6KcJwROVXKPhwPRzLOKQd+zSY/pIALT0ZD0n176CSmmMTpc6+QVETV5X6Pe9MXVdA1T8QHxvvDA+ygECQQD/CBRuuJC2fXabhQeHKa94dzv98g82my/m4r9TRyyUSwpciT1sY+0nfm8DARqROkIJ8iJU5xt+Yf2b2LZDeHeZAkEA5DfuO7kEl/QGTsPP3GKWT31H3hneztHS78aB/qrFJptxLMey+J6wwcE0G+so5k+UWi55MGKtiAWgQ5EmcrI1QQJAcuPc8JRM/SlASYeAgK+S0R5F9H0bxWncBpOXxZiGyLeVj2J0PWQ27lfTAvN4WHx6S6i9Nqp2hFT4v0C9u1+F4QJAbbIIm8JR1+wegAuUxNzKXQjd237Z3tVyK3hiEZPp0aXTn2+ZsfEtCuSf9G9zKEjGCRbff4de28vAfdmt/mF0QQJADlS6M9xgL9ZotRZFhmkvVJ5FvHU4717oTFTl8iJGpFNuxuzi3Szq12X72Wv6gLsUg2ap6a64GfClNg/gNo5pAw==";
	
	private DefaultAlipayClient alipay_client;
	private AlipayTradePrecreateRequest alipay_pre_trade_req;
	void initAlipayClient() {
		//实例化客户端
		alipay_client = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do",
				AliAppid,
				APP_PRIVATE_KEY,
				"json",
				"utf8",
				ALIPAY_PUBLIC_KEY);
		alipay_pre_trade_req = new AlipayTradePrecreateRequest();//创建API对应的request类
	}
	void tradePreOrder(String no, float price, String operator) {
		ParamsMap param = new ParamsMap();
		// 参数具体含义ref: https://doc.open.alipay.com/docs/api.htm?spm=a219a.7386797.0.0.2cPt2c&docType=4&apiId=862
		param.addKV("out_trade_no", no)
			.addKV("total_amount", price)
			.addKV("subject", "test alipay")
			.addKV("operator_id", operator)
			.addKV("timeout_express", "30m");
		System.out.println("param json:\n" + param.toJson());
		alipay_pre_trade_req.setBizContent(param.toJson());
		try {
			AlipayTradePrecreateResponse response = alipay_client.execute(alipay_pre_trade_req);
			//调用成功，则处理业务逻辑
			if (response.isSuccess()) {
				System.out.println("qr code url:" + response.getQrCode());
				// 当面付异步通知：https://doc.open.alipay.com/doc2/detail.htm?treeId=194&articleId=103296&docType=1#s5
			}
		} catch (AlipayApiException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	String waitOrderStatus(String no) throws InterruptedException {
		AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
		
		ParamsMap p = new ParamsMap();
		p.addKV("out_trade_no", no);
		req.setBizContent(p.toJson());

		AlipayTradeQueryResponse resp;
		try {
			resp = alipay_client.execute(req);
			String s = resp.getTradeStatus();
			while (s == "WAIT_BUYER_PAY") {
				resp = alipay_client.execute(req);
				s = resp.getTradeStatus();
				Thread.sleep(5*1000);
				System.out.println("waiting for pay status ...");
			}
			return s;
		} catch (AlipayApiException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getErrMsg());
			e.printStackTrace();
		}
		return "Unrecognized error";

	}
	public static void main(String[] args) throws IOException, InterruptedException {
		ServerSocket listener = new ServerSocket(8686);
		Server myserver = new Server();
		myserver.initAlipayClient();
        try {
            while (true) {
            	System.out.println("init a new thread...");
            	new worker(listener.accept(), myserver).start();
            }
        }
        finally {
            listener.close();
        }
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
        	BufferedReader in = new BufferedReader(
        	        new InputStreamReader(sk.getInputStream()));
            // 由其他程序发送过来的message必须以\n或\r结尾
            String input = in.readLine();
        	PrintWriter out =
                new PrintWriter(sk.getOutputStream(), true);

        	String seg[] = input.split(":");
        	float price = Float.parseFloat(seg[1]);
        	String tradeNo = seg[0];
        	s.tradePreOrder(tradeNo, price, seg[2]);
        	
        	String ret = s.waitOrderStatus(tradeNo);
        	out.println("alipay status: " + ret);
        	sk.close();
        } catch (Exception e) {
        	System.out.println(e.getMessage());
        }
    }
}