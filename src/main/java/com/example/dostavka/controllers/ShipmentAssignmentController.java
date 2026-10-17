package com.example.dostavka.controllers;

import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.services.ShipmentAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipment-assignments")
public class ShipmentAssignmentController {

    private final ShipmentAssignmentService assignmentService;

    public ShipmentAssignmentController(ShipmentAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<List<ShipmentAssignment>> getAll() {
        return ResponseEntity.ok(assignmentService.getAllAssignments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentAssignment> getById(@PathVariable Long id) {
        ShipmentAssignment assignment = assignmentService.getAssignmentById(id);
        return assignment != null ? ResponseEntity.ok(assignment) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ShipmentAssignment> create(
            @RequestParam Long shipmentId,
            @RequestParam Long stopId) {
        ShipmentAssignment created = assignmentService.createAssignment(shipmentId, stopId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShipmentAssignment> update(
            @PathVariable Long id,
            @RequestParam Long newStopId) {
        ShipmentAssignment updated = assignmentService.updateAssignmentStop(id, newStopId);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}