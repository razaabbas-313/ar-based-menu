package com.atelier.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "analytics_daily")
@IdClass(AnalyticsDailyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDaily {

    @Id
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Id
    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Id
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Id
    @Column(name = "\"day\"", nullable = false)
    private LocalDate day;

    @Column(name = "event_count", nullable = false)
    private Long eventCount;
}
