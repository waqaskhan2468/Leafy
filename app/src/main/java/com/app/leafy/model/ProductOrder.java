package com.app.leafy.model;

import java.io.Serializable;

public class ProductOrder implements Serializable {

    public String buyer;
    public String address;
    public String email;
    public String shipping;
    public Long date_ship;
    public String phone;
    public String comment;
    public String status;
    public Double total_fees;
    public Double DeliveryCharges;
    public String serial;
    public Long created_at = System.currentTimeMillis();
    public Long last_update = System.currentTimeMillis();

    public ProductOrder() {
    }

    public ProductOrder(BuyerProfile buyerProfile, String comment) {
        this.buyer = buyerProfile.name;
        this.address = buyerProfile.address+" "+buyerProfile.area;
        this.email = buyerProfile.email;
        this.phone = buyerProfile.phone;
        this.comment = comment;
        this.date_ship=created_at;
    }
}
