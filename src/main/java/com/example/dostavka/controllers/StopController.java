package com.example.dostavka.controllers;

import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.models.Stop;
import com.example.dostavka.services.StopService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final StopService stopService;

    public StopController(StopService stopService) {
        this.stopService = stopService;
    }

    @GetMapping
    public ResponseEntity<List<Stop>> getAll() {
        return ResponseEntity.ok(stopService.getAllStops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Stop> getById(@PathVariable Long id) {
        Stop stop = stopService.getStopById(id);
        return stop != null ? ResponseEntity.ok(stop) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Stop> create(@RequestBody Stop stop, @RequestParam Long routeId) {
        Stop created = stopService.createStop(stop, routeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Stop> update(@PathVariable Long id, @RequestBody Stop updates) {
        Stop updated = stopService.updateStop(id, updates);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stopService.deleteStop(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{stopId}/deliver")
    public ResponseEntity<List<ShipmentAssignment>> deliverAtStop(@PathVariable Long stopId) {
        List<ShipmentAssignment> delivered = stopService.completeStopDelivery(stopId);
        return ResponseEntity.ok(delivered);
    }
}