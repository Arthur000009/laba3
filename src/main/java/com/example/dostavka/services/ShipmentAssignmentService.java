package com.example.dostavka.services;

import com.example.dostavka.models.Route;
import com.example.dostavka.models.Shipment;
import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.models.Stop;
import com.example.dostavka.repositories.ShipmentAssignmentRepository;
import com.example.dostavka.repositories.ShipmentRepository;
import com.example.dostavka.repositories.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShipmentAssignmentService {

    private final ShipmentAssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;
    private final StopRepository stopRepository;
    private final RouteService routeService;
    private final ShipmentService shipmentService;

    public ShipmentAssignmentService(
            ShipmentAssignmentRepository assignmentRepository,
            ShipmentRepository shipmentRepository,
            StopRepository stopRepository,
            RouteService routeService,
            ShipmentService shipmentService) {
        this.assignmentRepository = assignmentRepository;
        this.shipmentRepository = shipmentRepository;
        this.stopRepository = stopRepository;
        this.routeService = routeService;
        this.shipmentService = shipmentService;
    }

    public List<ShipmentAssignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public ShipmentAssignment getAssignmentById(Long id) {
        return assignmentRepository.findById(id).orElse(null);
    }

    @Transactional
    public ShipmentAssignment createAssignment(Long shipmentId, Long stopId) {
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new IllegalArgumentException("Остановка с ID " + stopId + " не найдена"));
        return routeService.assignShipmentToRouteStop(stop.getRoute().getId(), stopId, shipmentId);
    }

    @Transactional
    public ShipmentAssignment updateAssignmentStop(Long id, Long newStopId) {
        ShipmentAssignment existing = assignmentRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        return shipmentService.transferShipment(existing.getShipment().getId(), newStopId);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        ShipmentAssignment existing = assignmentRepository.findById(id).orElse(null);
        if (existing == null) {
            return;
        }
        Route route = existing.getStop().getRoute();
        if (!"PLANNED".equals(route.getStatus())) {
            throw new IllegalStateException("Нельзя удалить назначение: маршрут уже выполняется или завершен (статус " + route.getStatus() + ")");
        }
        Shipment shipment = existing.getShipment();
        if ("ASSIGNED".equals(shipment.getStatus())) {
            shipment.setStatus("CREATED");
            shipmentRepository.save(shipment);
        }
        assignmentRepository.delete(existing);
    }
}