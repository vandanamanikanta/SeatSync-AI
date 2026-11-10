package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSearchCriteria {

    private String search;
    private String category;
    private String city;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Instant startDate;
    private Instant endDate;
    private EventStatus status;

    public Specification<Event> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by search keyword matching title or short description
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("shortDescription")), searchPattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            // Filter by category (by slug or name or id)
            if (category != null && !category.isBlank()) {
                String cat = category.trim().toLowerCase();
                try {
                    Long catId = Long.parseLong(cat);
                    predicates.add(cb.equal(root.get("category").get("id"), catId));
                } catch (NumberFormatException e) {
                    Predicate slugMatch = cb.equal(cb.lower(root.get("category").get("slug")), cat);
                    Predicate nameMatch = cb.equal(cb.lower(root.get("category").get("name")), cat);
                    predicates.add(cb.or(slugMatch, nameMatch));
                }
            }

            // Filter by venue city
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("venue").get("city")), city.trim().toLowerCase()));
            }

            // Price range filter
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice));
            }

            // Date range filter
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), endDate));
            }

            // Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

