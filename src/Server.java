import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alipay.api.AlipayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.demo.trade.config.Configs;
import com.alipay.demo.trade.model.builder.AlipayTradePrecreateRequestBuilder;
import com.alipay.demo.trade.model.builder.AlipayTradeQueryRequestBuilder;
import com.alipay.demo.trade.model.result.AlipayF2FPrecreateResult;
import com.alipay.demo.trade.service.AlipayTradeService;
import com.alipay.demo.trade.service.impl.AlipayTradeServiceImpl;


public class Server {
	private static Log log = LogFactory.getLog(Server.class);
    // 支付宝当面付2.0服务
    private static AlipayTradeService tradeService;
    static {
        /** 一定要在创建AlipayTradeService之前调用Configs.init()设置默认参数
         *  Configs会读取classpath下的zfbinfo.properties文件配置信息，如果找不到该文件则确认该文件是否在classpath目录
         */
        Configs.init("zfbinfo.properties");

        /** 使用Configs提供的默认参数
         *  AlipayTradeService可以使用单例或者为静态成员对象，不需要反复new
         */
        tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();
    }
    
 // 简单打印应答
    private void dumpResponse(AlipayResponse response) {
        if (response != null) {
            log.info(String.format("code:%s, msg:%s", response.getCode(), response.getMsg()));
            String sub_code = response.getSubCode();
            if (sub_code != null && sub_code.equals("")) {
                log.info(String.format("subCode:%s, subMsg:%s", response.getSubCode(), response.getSubMsg()));
            }
            log.info("body:" + response.getBody());
        }
    }
    
    public String trade_precreate(String price, String operator, String trade_no) {
        // (必填) 商户网站订单系统中唯一订单号，64个字符以内，只能包含字母、数字、下划线，
        // 需保证商户系统端不能重复，建议通过数据库sequence生成，
        String outTradeNo = trade_no;

        // (必填) 订单标题，粗略描述用户的支付目的。如“xxx品牌xxx门店当面付扫码消费”
        String subject = "LESS & MORE 门店当面付扫码消费";

        // (必填) 订单总金额，单位为元，不能超过1亿元
        // 如果同时传入了【打折金额】,【不可打折金额】,【订单总金额】三者,则必须满足如下条件:【订单总金额】=【打折金额】+【不可打折金额】
        String totalAmount = price;

        // (可选) 订单不可打折金额，可以配合商家平台配置折扣活动，如果酒水不参与打折，则将对应金额填写至此字段
        // 如果该值未传入,但传入了【订单总金额】,【打折金额】,则该值默认为【订单总金额】-【打折金额】
        String undiscountableAmount = "0";

        // 卖家支付宝账号ID，用于支持一个签约账号下支持打款到不同的收款账号，(打款到sellerId对应的支付宝账号)
        // 如果该字段为空，则默认为与支付宝签约的商户的PID，也就是appid对应的PID
        String sellerId = "";

        // 订单描述，可以对交易或商品进行一个详细地描述，比如填写"购买商品2件共15.00元"
        String body = "购买商品3件共20.00元";

        // 商户操作员编号，添加此参数可以为商户操作员做销售统计
        String operatorId = operator;

        // (可选) 商户门店编号，通过门店号和商家后台可以配置精准到门店的折扣信息，详询支付宝技术支持
        String storeId = "xxx";

//        ExtendParams extendParams = new ExtendParams();
        // 业务扩展参数，目前可添加由支付宝分配的系统商编号(通过setSysServiceProviderId方法)，详情请咨询支付宝技术支持
//        extendParams.setSysServiceProviderId("2088100200300400500");

        // 支付超时，定义为120分钟
        String timeoutExpress = "10m";


        // 创建扫码支付请求builder，设置请求参数
        AlipayTradePrecreateRequestBuilder builder = new AlipayTradePrecreateRequestBuilder()
                .setSubject(subject)
                .setTotalAmount(totalAmount)
                .setOutTradeNo(outTradeNo)
                .setUndiscountableAmount(undiscountableAmount)
                .setSellerId(sellerId)
                .setBody(body)
                .setOperatorId(operatorId)
                .setStoreId(storeId)
                .setTimeoutExpress(timeoutExpress);

        AlipayF2FPrecreateResult result = tradeService.tradePrecreate(builder);
        switch (result.getTradeStatus()) {
            case SUCCESS:
                log.info("支付宝预下单成功: )");

                AlipayTradePrecreateResponse response = result.getResponse();
                dumpResponse(response);
                return response.getQrCode();
                // 需要修改为运行机器上的路径
//                String filePath = String.format("~/Documents/eclipse_work/AlipayServer/qr-%s.png", response.getOutTradeNo());
//                log.info("filePath:" + filePath);
//                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, filePath);

            case FAILED:
                log.error("支付宝预下单失败!!!");
                break;

            case UNKNOWN:
                log.error("系统异常，预下单状态未知!!!");
                break;

            default:
                log.error("不支持的交易状态，交易返回异常!!!");
                break;
        }
        return "";
    }
    
	String wait_order_status(String no) throws InterruptedException {
		AlipayTradeQueryRequestBuilder builder = new AlipayTradeQueryRequestBuilder();
		builder.setOutTradeNo(no);
		AlipayTradeQueryResponse resp = tradeService.tradeQuery(builder);
		String s = resp.getTradeStatus();
		int try_time = 0,
			MAX_TRY = 100;
		System.out.printf("waiting for pay status ... [%d][%s]\n", try_time, s);
		while ((s == null || s.equals("WAIT_BUYER_PAY")) && try_time < MAX_TRY) {
			resp = tradeService.tradeQuery(builder);
			s = resp.getTradeStatus();
			Thread.sleep(5 * 1000);
			try_time++;
			System.out.printf("waiting for pay status ... [%d][%s]\n", try_time, s);
		}
		if (try_time >= MAX_TRY) {
			return "Time out";
		} else {
			return s;
		}
	}
	public static void main(String[] args) throws IOException, InterruptedException {
		ServerSocket listener = new ServerSocket(8686);
		Server myserver = new Server();
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
        	String trade_no = seg[0];
        	String price = seg[1];
        	String operator = seg[2];
        	String qr_code = s.trade_precreate(price, operator, trade_no);
        	if (!qr_code.equals("")) {
        		System.out.println("QR: " + qr_code);
        		out.println("QR:" + qr_code);
        		out.flush();
        	}
        	Thread.sleep(5000);
        	String status = s.wait_order_status(trade_no);
        	out.println("trade status: " + status);
        	sk.close();
        } catch (Exception e) {
        	System.out.println(e.getMessage());
        }
    }
}