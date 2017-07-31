package com.pay.aile.meituan.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pay.aile.meituan.bean.Constants;
import com.pay.aile.meituan.bean.jpa.Order;
import com.pay.aile.meituan.bean.jpa.OrderItem;
import com.pay.aile.meituan.bean.jpa.OrderRefundStatusEnum;
import com.pay.aile.meituan.bean.jpa.OrderStatusEnum;
import com.pay.aile.meituan.bean.jpa.Platform;
import com.pay.aile.meituan.bean.jpa.RefundOrder;
import com.pay.aile.meituan.bean.jpa.RefundTypeEnum;
import com.pay.aile.meituan.bean.jpa.Shop;
import com.pay.aile.meituan.bean.jpa.StatusEnum;
import com.pay.aile.meituan.bean.platform.CancelOrderBean;
import com.pay.aile.meituan.bean.platform.NewOrderBean;
import com.pay.aile.meituan.bean.platform.NewOrderDetailBean;
import com.pay.aile.meituan.bean.platform.PayTypeEnum;
import com.pay.aile.meituan.bean.platform.RefundNotifyTypeEnum;
import com.pay.aile.meituan.bean.platform.RefundOrderBean;
import com.pay.aile.meituan.bean.push.PushCancelOrder;
import com.pay.aile.meituan.bean.push.PushNewOrder;
import com.pay.aile.meituan.bean.push.PushOrderStatusChange;
import com.pay.aile.meituan.bean.push.PushRefundOrder;
import com.pay.aile.meituan.client.JpaClient;
import com.pay.aile.meituan.client.TakeawayClient;
import com.pay.aile.meituan.sdk.MeituanConfig;
import com.pay.aile.meituan.util.JsonFormatUtil;
import com.sankuai.sjst.platform.developer.domain.RequestSysParams;
import com.sankuai.sjst.platform.developer.request.CipCaterTakeoutOrderCancelRequest;
import com.sankuai.sjst.platform.developer.request.CipCaterTakeoutOrderConfirmRequest;
import com.sankuai.sjst.platform.developer.request.CipCaterTakeoutOrderRefundAcceptRequest;
import com.sankuai.sjst.platform.developer.request.CipCaterTakeoutOrderRefundRejectRequest;

/**
 *
 * @Description: 这里用一句话描述这个类的作用
 * @see: OrderService 此处填写需要参考的类
 * @version 2017年7月18日 上午10:24:44
 * @author chao.wang
 */
@Service
public class OrderService {

    private static final SimpleDateFormat sdf = new SimpleDateFormat(
            "yyyy-MM-dd hh:mm:ss");
    private Logger logger = LoggerFactory.getLogger(getClass());
    /** jpa项目order接口 */
    @Resource
    private JpaClient jpaClient;
    /** 推送接口 */
    @Resource
    private TakeawayClient takeawayClient;

    /**
     *
     * @Description 商家拒绝订单
     * @param shopId
     * @param orderId
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void cancelOrder(String shopId, Long orderId, String reasonCode,
            String reason) {
        // 通过shopId获取appAuthToken
        String appAuthToken = MeituanConfig.getAppAuthToken(shopId);
        RequestSysParams sysParams = new RequestSysParams();
        sysParams.setAppAuthToken(appAuthToken);
        sysParams.setSecret(MeituanConfig.getSignkey());
        CipCaterTakeoutOrderCancelRequest request = new CipCaterTakeoutOrderCancelRequest();
        request.setOrderId(orderId);
        request.setRequestSysParams(sysParams);
        request.setReasonCode(reasonCode);
        request.setReason(reason);
        String result = "";
        try {
            result = request.doRequest();
        } catch (Exception e) {
            logger.error("cancelOrder error!orderId={},result={}", orderId,
                    result, e);
            throw new RuntimeException("调用美团取消订单失败!" + e.getMessage());
        }
        if (Constants.ok.equals(result)) {
            logger.info("cancelOrder success!orderId={}", orderId);
            try {
                Order order = new Order();
                order.setOrderId(orderId.toString());
                order.setStatus(OrderStatusEnum.invalid);
                order.setCancelReason(reason);
                fillShop(shopId, order);
                jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            } catch (Exception e) {
                logger.error("cancelOrder修改订单状态失败!orderId={}", orderId, e);
                throw new RuntimeException(
                        "美团取消订单成功,修改本地订单状态失败!" + e.getMessage());
            }
        } else {
            logger.info("cancelOrder fail!orderId={},result={}", orderId,
                    result);
            throw new RuntimeException(
                    String.format("调用美团取消订单失败!result={%s}", result));
        }

    }

    /**
     *
     * @Description 处理美团推送的已取消订单
     * @param shopId
     * @param orderId
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void cancelOrderPush(String shopId, @Valid CancelOrderBean bean) {
        long time = System.currentTimeMillis();
        Long orderId = bean.getOrderId();
        // 保存订单
        JSONObject result = null;
        try {
            Order order = new Order();
            order.setOrderId(orderId.toString());
            order.setStatus(OrderStatusEnum.invalid);
            order.setUpdateTime(time);
            fillShop(shopId, order);
            logger.info("cancelOrderPush 修改订单状态,order={}",
                    JsonFormatUtil.toJSONString(order));
            result = jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            logger.info("cancelOrderPush 修改订单状态返回={}", result);
        } catch (Exception e) {
            logger.error("cancelOrderPush修改订单状态失败!orderId={}", orderId, e);
            throw new RuntimeException("美团取消订单成功,修改本地订单状态失败!" + e.getMessage());
        }
        Long id = getPrimaryKeyFromOrder(result);
        // 向POS推送取消订单信息
        PushCancelOrder pushCancelOrder = new PushCancelOrder(id);
        pushCancelOrder.setOrderId(orderId.toString());
        // pushCancelOrder.setReason(bean.getReason());
        pushCancelOrder.setUpdateTime(time);
        JSONObject pushResult = null;
        try {
            logger.info("cancelOrderPush 推送的消息={}", pushCancelOrder);
            pushResult = takeawayClient.pushOrderCancel(
                    MeituanConfig.getRegistrationId(shopId),
                    JsonFormatUtil.toJSONString(pushCancelOrder));
            logger.info("cancelOrderPush 推送结果={}", pushResult);
        } catch (Exception e) {
            logger.error("cancelOrderPush 处理美团推送的已取消订单失败！orderId={}",
                    bean.getOrderId());
        }
        if (pushResult == null || !"0".equals(pushResult.getString("code"))) {
            logger.error("cancelOrderPush 极光推送失败!msg={},pushBean={}",
                    pushResult == null ? "" : pushResult.getString("msg"),
                    bean);
        }
    }

    /**
     *
     * @Description 接单
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void confirmOrder(String shopId, Long orderId) {
        // 通过shopId获取appAuthToken
        String appAuthToken = MeituanConfig.getAppAuthToken(shopId);
        RequestSysParams sysParams = new RequestSysParams();
        sysParams.setAppAuthToken(appAuthToken);
        sysParams.setSecret(MeituanConfig.getSignkey());
        CipCaterTakeoutOrderConfirmRequest request = new CipCaterTakeoutOrderConfirmRequest();
        request.setOrderId(orderId);
        request.setRequestSysParams(sysParams);
        String result = "";
        try {
            result = request.doRequest();
        } catch (Exception e) {
            logger.error("confirmOrder error!orderId={},result={}", orderId,
                    result, e);
            throw new RuntimeException("调用美团确认订单失败!" + e.getMessage());
        }
        if (Constants.ok.equals(result)) {
            logger.info("confirmOrder success!orderId={}", orderId);
        } else {
            logger.info("confirmOrder fail!orderId={},result={}", orderId,
                    result);
            throw new RuntimeException(
                    String.format("调用美团确认订单失败!result={%s}", result));
        }
    }

    /**
     *
     * @Description 商家已确认订单推送
     * @param shopId
     * @param newOrderBean
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    @RequestMapping("/confirmOrderPush")
    public void confirmOrderPush(String shopId,
            @Valid NewOrderBean newOrderBean) {
        long updateTime = System.currentTimeMillis();
        // 修改订单状态
        Long orderId = newOrderBean.getOrderId();
        JSONObject result = null;
        try {
            Order order = new Order();
            order.setOrderId(orderId.toString());
            order.setStatus(OrderStatusEnum.valid);
            order.setUpdateTime(updateTime);
            fillShop(shopId, order);
            logger.info("confirmOrderPush 修改订单状态,order={}",
                    JsonFormatUtil.toJSONString(order));
            result = jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            logger.info("confirmOrderPush 修改订单状态返回={}", result);
        } catch (Exception e) {
            logger.error("confirmOrderPush 修改订单状态失败!orderId={}", orderId, e);
            throw new RuntimeException("美团确认订单成功,修改本地订单状态失败!" + e.getMessage());
        }
        Long id = getPrimaryKeyFromOrder(result);
        // 推送订单状态变化
        PushOrderStatusChange bean = new PushOrderStatusChange(id);
        bean.setOrderId(newOrderBean.getOrderId());
        bean.setStatus(OrderStatusEnum.confirmed.getCode());
        bean.setUpdateTime(updateTime);
        JSONObject pushResult = null;
        try {
            logger.info("confirmOrderPush 推送的消息={}", bean);
            pushResult = takeawayClient.pushOrderChange(
                    MeituanConfig.getRegistrationId(shopId),
                    JsonFormatUtil.toJSONString(bean));
            logger.info("confirmOrderPush 推送结果={}", pushResult);
        } catch (Exception e) {
            logger.error("confirmOrderPush 推送商家已确认订单失败！orderId={}",
                    bean.getOrderId());
        }
        if (pushResult == null || !"0".equals(pushResult.getString("code"))) {
            logger.error("confirmOrderPush 极光推送失败!msg={},pushBean={}",
                    pushResult == null ? "" : pushResult.getString("msg"),
                    bean);
        }
    }

    /**
     *
     * @Description 处理推送的已完成订单
     * @param baseBean
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void finishOrderPush(String shopId, @Valid NewOrderBean bean) {
        long updateTime = System.currentTimeMillis();
        // 修改本地库中订单状态
        Long orderId = bean.getOrderId();
        JSONObject result = null;
        try {
            Order order = new Order(bean.getOrderId().toString());
            order.setStatus(OrderStatusEnum.completed);
            order.setUpdateTime(updateTime);
            fillShop(shopId, order);
            logger.info("finishOrderPush 修改订单状态,order={}",
                    JsonFormatUtil.toJSONString(order));
            result = jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            logger.info("finishOrderPush 修改订单状态返回={}", result);
        } catch (Exception e) {
            logger.error("finishOrderPush 修改订单状态失败!orderId={}", orderId, e);
            throw new RuntimeException(
                    "处理推送的已完成订单成功,修改本地订单状态失败!" + e.getMessage());
        }
        Long id = getPrimaryKeyFromOrder(result);
        // 推送订单状态变化
        PushOrderStatusChange changeBean = new PushOrderStatusChange(id);
        changeBean.setOrderId(bean.getOrderId());
        changeBean.setStatus(OrderStatusEnum.confirmed.getCode());
        changeBean.setUpdateTime(updateTime);
        JSONObject pushResult = null;
        try {
            logger.info("finishOrderPush 推送的消息={}", changeBean);
            pushResult = takeawayClient.pushOrderChange(
                    MeituanConfig.getRegistrationId(shopId),
                    JsonFormatUtil.toJSONString(changeBean));
            logger.info("finishOrderPush 推送结果={}", pushResult);
        } catch (Exception e) {
            logger.error("finishOrderPush 推送已完成订单失败！orderId={}",
                    bean.getOrderId());
        }
        if (pushResult == null || !"0".equals(pushResult.getString("code"))) {
            logger.error("finishOrderPush 极光推送失败!msg={},pushBean={}",
                    pushResult == null ? "" : pushResult.getString("msg"),
                    bean);
        }
    }

    /**
     *
     * @Description 推送并保存新订单
     * @param orderPushBean
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void newOrderPush(String shopId, @Valid NewOrderBean newOrderBean) {
        // 根据shopId获取门店是否设置了自动接单,若自动接单,则最后要调用美团接单接口
        long time = System.currentTimeMillis();
        List<NewOrderDetailBean> newOrderDetaiList = newOrderBean
                .getDetailList();

        Order order = new Order();
        List<OrderItem> itemList = new ArrayList<OrderItem>();
        for (NewOrderDetailBean detail : newOrderDetaiList) {
            // 数据库entity
            BigDecimal price = new BigDecimal(detail.getPrice().toString());
            BigDecimal quantity = new BigDecimal(detail.getQuantity());
            BigDecimal boxPrice = new BigDecimal(
                    detail.getBox_price().toString());
            BigDecimal boxNum = new BigDecimal(detail.getBox_num().toString());
            BigDecimal totalAmount = price.multiply(quantity)
                    .add(boxPrice.multiply(boxNum));
            OrderItem item = new OrderItem();
            item.setFoodName(detail.getFood_name());
            item.setOrder(order);
            item.setPrice(price);
            item.setQuantity(quantity.intValue());
            item.setTotalAmount(totalAmount);
            itemList.add(item);
        }

        // 保存订单信息
        order.setOrderId(newOrderBean.getOrderId().toString());
        order.setAddress(newOrderBean.getRecipientAddress());
        order.setConsignee(newOrderBean.getRecipientName());
        order.setDaySn(Long.valueOf(newOrderBean.getDaySeq()));
        order.setDeliverFee(
                BigDecimal.valueOf(newOrderBean.getShippingFee() == null ? 0D
                        : newOrderBean.getShippingFee()));
        order.setDeliverTime(newOrderBean.getDeliveryTime() == 0 ? ""
                : sdf.format(new Date(newOrderBean.getDeliveryTime() * 1000)));
        order.setDescription(newOrderBean.getCaution());
        order.setHasInvoiced(StatusEnum.get(newOrderBean.getHasInvoiced()));
        order.setIncome(BigDecimal.valueOf(newOrderBean.getTotal()));// 商铺实收
        order.setInvoiceTitle(newOrderBean.getInvoiceTitle());
        order.setItemList(itemList);
        order.setLatitude(newOrderBean.getLatitude());
        order.setLongitude(newOrderBean.getLongitude());
        order.setOnlinePaid(PayTypeEnum.onlinePay(newOrderBean.getPayType())
                ? StatusEnum.ENABLE : StatusEnum.DISENABLE);
        order.setOrderCreateTime(
                sdf.format(new Date(newOrderBean.getCtime() * 1000)));
        order.setOriginalPrice(
                BigDecimal.valueOf(newOrderBean.getOriginalPrice()));
        order.setPackageFee(BigDecimal.ZERO);// 美团没有餐盒费字段
        order.setPhone(newOrderBean.getRecipientPhone());
        order.setStatus(OrderStatusEnum.unprocessed);
        order.setTotalPrice(BigDecimal.valueOf(newOrderBean.getTotal()));
        order.setUpdateTime(time);
        fillShop(shopId, order);
        Long orderId = newOrderBean.getOrderId();
        JSONObject result = null;
        try {
            logger.info("newOrderPush 保存订单,order={}", order);
            result = jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            logger.info("newOrderPush 保存订单返回={}", result);
        } catch (Exception e) {
            logger.error("pushSaveNewOrder saveOrder error!orderId={}", orderId,
                    e);
            throw new RuntimeException("调用jpa接口保存订单失败!" + e.getMessage());
        }
        Long id = getPrimaryKeyFromOrder(result);

        // 推送到POS的订单信息
        PushNewOrder pushOrder = new PushNewOrder(id);
        pushOrder.setAddress(newOrderBean.getRecipientAddress());
        pushOrder.setConsignee(newOrderBean.getRecipientName());
        pushOrder.setOnlinePaid(PayTypeEnum.onlinePay(newOrderBean.getPayType())
                ? StatusEnum.ENABLE.toString()
                : StatusEnum.DISENABLE.toString());
        pushOrder.setOrderCreateTime(
                sdf.format(new Date(newOrderBean.getCtime())));
        pushOrder.setDeliverTime(
                sdf.format(new Date(newOrderBean.getDeliveryTime())));
        pushOrder.setPhone(newOrderBean.getRecipientPhone());
        pushOrder.setOrderId(newOrderBean.getOrderId().toString());

        String pushOrderJson = JsonFormatUtil.toJSONString(pushOrder);
        JSONObject pushResult = null;
        try {
            String registrationId = MeituanConfig.getRegistrationId(shopId);
            logger.info("newOrderPush 推送信息 pushOrder={}", pushOrderJson);
            pushResult = takeawayClient.pushNewOrder(registrationId,
                    pushOrderJson);
            logger.info("newOrderPush 推送返回：{}", pushResult);
        } catch (Exception e) {
            logger.error("pushSaveNewOrder 极光推送失败!pushOrder={}", pushOrder);
        }
        if (pushResult == null || !"0".equals(pushResult.getString("code"))) {
            logger.error("pushSaveNewOrder 极光推送失败!msg={},pushOrder={}",
                    pushResult == null ? "" : pushResult.getString("msg"),
                    pushOrder);
        }
    }

    /**
     *
     * @Description 同意退款
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void refundOrderConfirm(String shopId, Long orderId, String reason) {
        // 1.调用美团同意退款接口2.修改本地库中退款单状态3.修改本地库中订单状态
        // 根据shopId获取appAuthToken
        String appAuthToken = MeituanConfig.getAppAuthToken(shopId);
        RequestSysParams sysParams = new RequestSysParams();
        sysParams.setAppAuthToken(appAuthToken);
        sysParams.setSecret(MeituanConfig.getSignkey());
        CipCaterTakeoutOrderRefundAcceptRequest request = new CipCaterTakeoutOrderRefundAcceptRequest();
        request.setOrderId(orderId);
        request.setReason(reason);
        request.setRequestSysParams(sysParams);
        String result = "";
        try {
            result = request.doRequest();
        } catch (Exception e) {
            logger.error("refundOrderConfirm error!orderId={},result={}",
                    orderId, result, e);
            throw new RuntimeException("调用美团确认退款失败!" + e.getMessage());
        }
        if (Constants.ok.equals(result)) {
            logger.info("refundOrderConfirm success!orderId={}", orderId);
        } else {
            logger.info("refundOrderConfirm fail!orderId={},result={}", orderId,
                    result);
            throw new RuntimeException(
                    String.format("调用美团确认退款失败!result={%s}", result));
        }
    }

    /**
     *
     * @Description 订单退款推送消息处理
     * @param baseBean
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void refundOrderPush(String shopId, @Valid RefundOrderBean bean) {

        long updateTime = System.currentTimeMillis();
        // 推送bean
        PushRefundOrder pushRefundOrder = new PushRefundOrder();
        pushRefundOrder.setOrderId(bean.getOrderId().toString());
        pushRefundOrder.setUpdateTime(updateTime);

        // 订单bean
        Order order = new Order(bean.getOrderId().toString());
        order.setUpdateTime(updateTime);
        fillShop(shopId, order);
        // 退单bean
        RefundOrder refund = new RefundOrder();
        refund.setOrder(order);
        refund.setReason(bean.getReason());
        refund.setRefundType(RefundTypeEnum.normal);
        refund.setUpdateTime(updateTime);
        switch (RefundNotifyTypeEnum.getFromCode(bean.getNotifyType())) {
        case apply:// 用户申请退款
            // 订单状态为退款处理中
            order.setStatus(OrderStatusEnum.refunding);
            pushRefundOrder
                    .setRefundStatus(OrderRefundStatusEnum.applied.getCode());
            refund.setRefundStatus(OrderRefundStatusEnum.applied);
            break;
        case agree:// 商家同意退款
            order.setStatus(OrderStatusEnum.refunded);
            pushRefundOrder.setRefundStatus(
                    OrderRefundStatusEnum.successful.getCode());
            refund.setRefundStatus(OrderRefundStatusEnum.successful);
            break;
        case cancelRefund:// 用户取消退款申请
            order.setStatus(OrderStatusEnum.valid);
            pushRefundOrder
                    .setRefundStatus(OrderRefundStatusEnum.failed.getCode());
            refund.setRefundStatus(OrderRefundStatusEnum.failed);
            break;
        case cancelRefundComplaint:// 取消退款申诉
            order.setStatus(OrderStatusEnum.valid);
            pushRefundOrder
                    .setRefundStatus(OrderRefundStatusEnum.failed.getCode());
            refund.setRefundStatus(OrderRefundStatusEnum.failed);
            break;
        case reject:// 商家驳回退款
            order.setStatus(OrderStatusEnum.valid);
            pushRefundOrder
                    .setRefundStatus(OrderRefundStatusEnum.rejected.getCode());
            refund.setRefundStatus(OrderRefundStatusEnum.rejected);
            break;
        default:
            break;
        }

        // 修改订单状态
        JSONObject result = null;
        try {
            logger.info("refundOrderPush 修改订单状态,order={}",
                    JsonFormatUtil.toJSONString(order));
            result = jpaClient.saveOrUpdateOrder(JSON.toJSONString(order));
            logger.info("refundOrderPush 修改订单状态返回={}", result);
        } catch (Exception e) {
            logger.error("refundOrderPush 修改订单状态失败!orderId={}",
                    bean.getOrderId(), e);
            throw new RuntimeException("退单处理修改美团订单状态失败" + e.getMessage());
        }
        Long id = getPrimaryKeyFromOrder(result);

        // 修改退款单状态
        JSONObject refundResult = null;
        try {
            logger.info("refundOrderPush 修改退款单状态,bean={}",
                    JsonFormatUtil.toJSONString(refund));
            refundResult = jpaClient
                    .saveOrUpdateRefundOrder(JSON.toJSONString(refund));
            logger.info("refundOrderPush 修改退款单状态返回={}", refundResult);
        } catch (Exception e) {
            logger.error("refundOrderPush 修改退款单状态失败!orderId={}",
                    bean.getOrderId(), e);
            throw new RuntimeException("退单处理修改美团退单状态失败" + e.getMessage());
        }

        pushRefundOrder.setId(id);
        // TODO 1.查询本地是否已有此退单记录,若有则更新,若无则保存
        // 推送
        JSONObject pushResult = null;
        try {
            logger.info("refundOrderPush 推送信息 ={}", pushRefundOrder);
            pushResult = takeawayClient.pushRefundOrder(
                    MeituanConfig.getRegistrationId(shopId),
                    JsonFormatUtil.toJSONString(pushRefundOrder));
            logger.info("refundOrderPush 推送返回结果 ={}", pushResult);
        } catch (Exception e) {
            logger.error("refundOrderPush 极光推送失败!pushOrder={}",
                    pushRefundOrder);
        }
        if (pushResult == null || !"0".equals(pushResult.getString("code"))) {
            logger.error("refundOrderPush 极光推送失败!msg={},pushOrder={}",
                    pushResult == null ? "" : pushResult.getString("msg"),
                    pushRefundOrder);
        }
    }

    /**
     *
     * @Description 拒绝退款
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    public void refundOrderRefuse(String shopId, Long orderId, String reason) {
        // 1.调用美团同意退款接口2.修改本地库中退款单状态3.修改本地库中订单状态
        // 根据shopId获取appAuthToken
        String appAuthToken = MeituanConfig.getAppAuthToken(shopId);
        RequestSysParams sysParams = new RequestSysParams();
        sysParams.setAppAuthToken(appAuthToken);
        sysParams.setSecret(MeituanConfig.getSignkey());
        CipCaterTakeoutOrderRefundRejectRequest request = new CipCaterTakeoutOrderRefundRejectRequest();
        request.setOrderId(orderId);
        request.setReason(reason);
        request.setRequestSysParams(sysParams);
        String result = "";
        try {
            result = request.doRequest();
        } catch (Exception e) {
            logger.error("refundOrderRefuse error!orderId={},result={}",
                    orderId, result, e);
            throw new RuntimeException("调用美团拒绝退款失败!" + e.getMessage());
        }
        if (Constants.ok.equals(result)) {
            logger.info("refundOrderRefuse success!orderId={}", orderId);
        } else {
            logger.info("refundOrderRefuse fail!orderId={},result={}", orderId,
                    result);
            throw new RuntimeException(
                    String.format("调用美团拒绝退款失败!result={%s}", result));
        }
    }

    /**
     *
     * @Description 给order设置美团shop
     * @param shopId
     * @param order
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    private void fillShop(String shopId, Order order) {
        order.setShop(new Shop(shopId, Platform.getInstance()));
    }

    /**
     *
     * @Description 获取保存或修改订单的返回值
     * @param jsonobject
     * @return
     * @see 需要参考的类或方法
     * @author chao.wang
     */
    private long getPrimaryKeyFromOrder(JSONObject jsonobject) {
        Assert.notNull(jsonobject, "保存或修改订单失败,返回值为空");
        Long id = jsonobject.getLong("id");
        if (id == null) {
            throw new IllegalArgumentException("保存或修改订单失败,返回id为空");
        }
        return id;

    }
}
