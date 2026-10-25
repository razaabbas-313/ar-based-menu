package com.atelier.media.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AnalyticsDailyId implements Serializable {
    private Long restaurantId;
    private Long dishId;
    private String eventType;
    private LocalDate day;
}
