export function isReservationTimeInPast(reservationTime: Date, nowMs = Date.now()): boolean {
  return reservationTime.getTime() < nowMs;
}
