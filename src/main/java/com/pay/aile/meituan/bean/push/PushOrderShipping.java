package com.pay.aile.meituan.bean.push;

import java.io.Serializable;

import com.pay.aile.meituan.bean.jpa.PlatformCodeEnum;

/**
 *
 * @Description: 推送配送信息bean
 * @see: PushOrderShipping 此处填写需要参考的类
 * @version 2017年7月27日 下午6:08:26
 * @author chao.wang
 */
public class PushOrderShipping extends PushBaseBean implements Serializable {

    /**
     * @author chao.wang
     */
    private static final long serialVersionUID = 3205921465233380085L;

    private String code = PlatformCodeEnum.mt.getCode();
    private String phone;
    private String orderId;
    private String name;
    private String distributionStatus;
    private Long updateTime;

    public PushOrderShipping() {
    }

    public PushOrderShipping(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public String getDistributionStatus() {
        return distributionStatus;
    }

    public String getName() {
        return name;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPhone() {
        return phone;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDistributionStatus(String distributionStatus) {
        this.distributionStatus = distributionStatus;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PushOrderShipping [code=");
        builder.append(code);
        builder.append(", phone=");
        builder.append(phone);
        builder.append(", orderId=");
        builder.append(orderId);
        builder.append(", name=");
        builder.append(name);
        builder.append(", distributionStatus=");
        builder.append(distributionStatus);
        builder.append(", updateTime=");
        builder.append(updateTime);
        builder.append(", id=");
        builder.append(id);
        builder.append("]");
        return builder.toString();
    }

}
