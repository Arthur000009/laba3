package com.example.dostavka.controllers;

import com.example.dostavka.models.Shipment;
import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.services.ShipmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public ResponseEntity<List<Shipment>> getAll() {
        return ResponseEntity.ok(shipmentService.getAllShipments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getById(@PathVariable Long id) {
        Shipment shipment = shipmentService.getShipmentById(id);
        return shipment != null ? ResponseEntity.ok(shipment) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Shipment> create(@RequestBody Shipment shipment, @RequestParam Long senderId) {
        Shipment created = shipmentService.createShipment(shipment, senderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Shipment> update(@PathVariable Long id, @RequestBody Shipment updates) {
        Shipment updated = shipmentService.updateShipment(id, updates);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{shipmentId}/transfer")
    public ResponseEntity<ShipmentAssignment> transferShipment(
            @PathVariable Long shipmentId,
            @RequestParam Long newStopId) {
        ShipmentAssignment transferred = shipmentService.transferShipment(shipmentId, newStopId);
        return ResponseEntity.ok(transferred);
    }

    @PostMapping("/{shipmentId}/cancel")
    public ResponseEntity<Shipment> cancelShipment(@PathVariable Long shipmentId) {
        Shipment cancelled = shipmentService.cancelShipment(shipmentId);
        return ResponseEntity.ok(cancelled);
    }
}