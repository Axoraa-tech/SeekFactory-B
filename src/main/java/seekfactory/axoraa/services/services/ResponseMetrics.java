package seekfactory.axoraa.services.services;

/**
 * How quickly and how often a factory quotes on the RFQs routed to it.
 * Both values are null until the factory has received RFQs in the response window.
 */
public record ResponseMetrics(Double responseRatePercent, Double avgResponseTimeHours) {
}
