package com.pay.aile.meituan.web;

import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.pay.aile.meituan.bean.Constants;
import com.pay.aile.meituan.bean.platform.ZbShippingFeeBean;
import com.pay.aile.meituan.service.DispatchService;
import com.pay.aile.meituan.util.JsonFormatUtil;

/**
 *
 * @Description: 配送接口
 * @see: DispatchController 此处填写需要参考的类
 * @version 2017年7月18日 下午2:00:51
 * @author chao.wang
 */
@RestController
@RequestMapping("/dispatch")
public class DispatchController {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private DispatchService dispatchService;

    /**
     *
     * @Description 自配送订单,订单已送达
     * @param shopId
     * @param orderId
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/selfDelivered")
    public JSONObject selfDelivered(String shopId, Long orderId) {
        try {
            dispatchService.selfDelivered(shopId, orderId);
            return JsonFormatUtil.getSuccessJson();
        } catch (Exception e) {
            logger.error("selfDelivered error!shopId={},orderId={}", shopId,
                    orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }

    /**
     *
     * @Description 订单自配送
     * @param shopId
     * @param orderId
     * @param courierName
     *            配送员姓名
     * @param courierPhone
     *            配送员电话
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/selfDelivering")
    public JSONObject selfDelivering(String shopId, Long orderId, String name,
            String phone) {
        try {
            dispatchService.selfDelivering(shopId, orderId, name, phone);
            return JsonFormatUtil.getSuccessJson();
        } catch (Exception e) {
            logger.error("selfDelivering error!shopId={},orderId={}", shopId,
                    orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }

    /**
     *
     * @Description 众包配送-确认下单(预下单失败后调用)
     * @param orderId
     * @param tipAmount
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/zbDispatchConfirm")
    public JSONObject zbDispatchConfirm(String shopId, Long orderId,
            Double tipAmount) {
        try {
            dispatchService.zbDispatchConfirm(shopId, orderId, tipAmount);
            return JsonFormatUtil.getSuccessJson();
        } catch (Exception e) {
            logger.error("zbDispatchConfirm error!shopId={},orderId={}", shopId,
                    orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }

    /**
     *
     * @Description 众包配送-预下单
     * @param orderId
     *            订单号
     * @param shippingFee
     *            配送费
     * @param tipAmount
     *            小费
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/zbDispatchPrepare")
    public JSONObject zbDispatchPrepare(String shopId, Long orderId,
            Double shippingFee, Double tipAmount) {
        try {
            String result = dispatchService.zbDispatchPrepare(shopId, orderId,
                    shippingFee, tipAmount);
            if (Constants.OK.equals(result)) {
                return JsonFormatUtil.getSuccessJson(result);
            } else {
                return JsonFormatUtil.getFailureJson(result);
            }
        } catch (Exception e) {
            logger.error("zbDispatchPrepare error!shopId={},orderId={}", shopId,
                    orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }

    /**
     *
     * @Description 众包配送-增加小费
     * @param orderId
     *            订单号
     * @param tipAmount
     *            消费金额
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/zbDispatchTipUpdate")
    public JSONObject zbDispatchTipUpdate(String shopId, Long orderId,
            Double tipAmount) {
        try {
            dispatchService.zbDispatchTipUpdate(shopId, orderId, tipAmount);
            return JsonFormatUtil.getSuccessJson();
        } catch (Exception e) {
            logger.error("zbDispatchTipUpdate error!shopId={},orderId={}",
                    shopId, orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }

    /**
     *
     * @Description 众包配送查询快递费
     * @param orderId
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/zbShippingFeeQuery")
    public JSONObject zbShippingFeeQuery(String shopId, String orderId) {
        try {
            List<ZbShippingFeeBean> list = dispatchService
                    .zbShippingFeeQuery(shopId, orderId);
            return JsonFormatUtil.getSuccessJson(list);
        } catch (Exception e) {
            logger.error("zbShippingFeeQuery error!shopId={},orderId={}",
                    shopId, orderId, e);
            return JsonFormatUtil.getFailureJson();
        }
    }
}
