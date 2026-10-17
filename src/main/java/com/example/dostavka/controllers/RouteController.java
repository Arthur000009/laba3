package com.example.dostavka.controllers;

import com.example.dostavka.models.Route;
import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.services.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ResponseEntity<List<Route>> getAll() {
        return ResponseEntity.ok(routeService.getAllRoutes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Route> getById(@PathVariable Long id) {
        Route route = routeService.getRouteById(id);
        return route != null ? ResponseEntity.ok(route) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Route> create(@RequestBody Route route) {
        Route created = routeService.createRoute(route);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Route> update(@PathVariable Long id, @RequestBody Route updates) {
        Route updated = routeService.updateRoute(id, updates);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{routeId}/assign-shipment")
    public ResponseEntity<ShipmentAssignment> assignShipment(
            @PathVariable Long routeId,
            @RequestParam Long stopId,
            @RequestParam Long shipmentId) {
        ShipmentAssignment assignment = routeService.assignShipmentToRouteStop(routeId, stopId, shipmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PostMapping("/{routeId}/start")
    public ResponseEntity<Route> startRoute(@PathVariable Long routeId) {
        Route started = routeService.startRoute(routeId);
        return ResponseEntity.ok(started);
    }
}