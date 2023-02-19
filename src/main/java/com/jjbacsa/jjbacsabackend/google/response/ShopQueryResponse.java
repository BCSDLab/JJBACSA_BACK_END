package com.jjbacsa.jjbacsabackend.google.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShopQueryResponse {

    private String place_id;
    private String name;
    private String formatted_address;
    private String x;
    private String y;
    private String open_now;
    private Integer totalRating;
    private Integer ratingCount;
    private String photoToken;

    private double dist;

    public boolean setShopCount(Integer totalRating, Integer ratingCount) {
        try {
            this.totalRating = totalRating;
            this.ratingCount = ratingCount;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setDist(double x, double y){
        double y_double = Double.parseDouble(this.y);
        double x_double = Double.parseDouble(this.x);

        double theta = y - y_double;
        double dist = Math.sin(deg2rad((x))) * Math.sin(deg2rad(x_double))
                + Math.cos(deg2rad(x)) * Math.cos(deg2rad(x_double)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist *= 60 * 1.1515 * 1609.344; //meter

        this.dist = dist;
    }

    public void setDist(){
        this.dist= Double.parseDouble(null);
    }
    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }
    private double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }


}
