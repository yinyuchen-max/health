package com.health.memory;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class ForgettingPolicy {
    /** Engineering activation model: access never changes the evidential truth of a fact. */
    public double activation(String category,LocalDateTime lastExposure,int usefulCount,double multiplier,LocalDateTime now) {
        double halfLife=switch(category) { case "health","family" -> 180; case "goal" -> 60; default -> 45; };
        double age=Math.max(0,Duration.between(lastExposure,now).toSeconds()/86400.0);
        double rehearsal=1+Math.min(1.5,Math.log1p(Math.max(0,usefulCount))*0.25);
        return Math.min(1,rehearsal*Math.pow(0.5,age/(halfLife*Math.max(0.5,Math.min(2,multiplier)))));
    }
    public boolean archive(String category,boolean pinned,double importance,double activation,boolean expired) {
        if(expired) return !pinned;
        if(pinned||"health".equals(category)||"family".equals(category)) return false;
        return activation<0.12&&importance<0.8;
    }
}
