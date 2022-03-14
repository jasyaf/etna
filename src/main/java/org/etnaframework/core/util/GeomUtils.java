package org.etnaframework.core.util;

import java.util.Collection;
/**
 * 地理位置相关的工具类
 *
 * @author BlackCat
 * @since 2015-09-18
 */
public class GeomUtils {

    /**
     * 地理坐标点，包含经纬度和高度信息
     */
    public static class Point {

        /** 纬度 */
        public Double lat;

        /** 经度 */
        public Double lon;

        /** 海拔高度，单位米 */
        public Double height;

        public Point() {
        }

        public Point(Double lat, Double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public Point(Double lat, Double lon, Double height) {
            this.lat = lat;
            this.lon = lon;
            this.height = height;
        }

        @Override
        public String toString() {
            return "Point [lat=" + lat + ", lon=" + lon + ", height=" + height + "]";
        }
    }

    /** 地球常量：赤道半径，单位米 */
    private static final double EARTH_RADIUS = 6378245;

    /**
     * 转换为弧度(rad)
     */
    private static double rad(double d) {
        return d * Math.PI / 180.0;
    }

    /**
     * 基于余弦定理求两经纬度距离，返回单位米
     *
     * @param lon1 第一点的经度
     * @param lat1 第一点的纬度
     * @param lon2 第二点的经度
     * @param lat2 第二点的纬度
     */
    public static double latitudeLongitudeDist(double lat1, double lon1, double lat2, double lon2) {
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);

        double radLon1 = rad(lon1);
        double radLon2 = rad(lon2);

        if (radLat1 < 0) {
            radLat1 = Math.PI / 2 + Math.abs(radLat1);// south
        }
        if (radLat1 > 0) {
            radLat1 = Math.PI / 2 - Math.abs(radLat1);// north
        }
        if (radLon1 < 0) {
            radLon1 = Math.PI * 2 - Math.abs(radLon1);// west
        }
        if (radLat2 < 0) {
            radLat2 = Math.PI / 2 + Math.abs(radLat2);// south
        }
        if (radLat2 > 0) {
            radLat2 = Math.PI / 2 - Math.abs(radLat2);// north
        }
        if (radLon2 < 0) {
            radLon2 = Math.PI * 2 - Math.abs(radLon2);// west
        }
        double x1 = EARTH_RADIUS * Math.cos(radLon1) * Math.sin(radLat1);
        double y1 = EARTH_RADIUS * Math.sin(radLon1) * Math.sin(radLat1);
        double z1 = EARTH_RADIUS * Math.cos(radLat1);

        double x2 = EARTH_RADIUS * Math.cos(radLon2) * Math.sin(radLat2);
        double y2 = EARTH_RADIUS * Math.sin(radLon2) * Math.sin(radLat2);
        double z2 = EARTH_RADIUS * Math.cos(radLat2);

        double d = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2));
        // 余弦定理求夹角
        double theta = Math.acos((EARTH_RADIUS * EARTH_RADIUS + EARTH_RADIUS * EARTH_RADIUS - d * d) / (2 * EARTH_RADIUS * EARTH_RADIUS));
        double dist = theta * EARTH_RADIUS;
        return dist;
    }

    /**
     * 判断点(lat, lon)是否在以(center_lat, center_lon)为圆心radius为半径（单位米）的圆中
     *
     * @param center_lat 圆心的纬度
     * @param center_lon 圆心的经度
     * @param radius 圆的半径，单位米
     * @param lat 需要判断的点纬度
     * @param lon 需要判断的点经度
     */
    public static boolean isInCircle(double center_lat, double center_lon, double radius, double lat, double lon) {
        boolean ret = false;
        // 到圆心的距离
        double dis = latitudeLongitudeDist(center_lat, center_lon, lat, lon);
        ret = !(dis > radius) && !(dis < -radius);
        return ret;
    }



    public static void main(String[] args) {
        System.out.println(latitudeLongitudeDist(29.87779409051713, 114.2624496394652, 29.87737007602842, 114.2621977794644));
    }
}
