use crate::schedule::*;

fn t(h: u32, m: u32) -> u32 {
    h * 3600 + m * 60
}

#[test]
fn at_15_50_shows_16_04_until_second_bus_departs() {
    let s = state_at(t(15, 50), 1);
    assert!(matches!(s.mode, Mode::Train));
    assert_eq!(s.train, Some(t(16, 4)));
    assert_eq!(s.next_train, Some(t(16, 34)));
    assert_eq!(s.bus1.as_ref().unwrap().countdown_min, 1);
    assert_eq!(s.bus2.as_ref().unwrap().countdown_min, 4);
    assert_eq!(s.arrive, None);
}

#[test]
fn at_16_13_still_shows_16_34() {
    let s = state_at(t(16, 13), 1);
    assert_eq!(s.train, Some(t(16, 34)));
    assert!(!s.bus1.as_ref().unwrap().catchable);
    assert!(!s.bus2.as_ref().unwrap().catchable);
}

#[test]
fn at_16_24_zero_countdown_then_rolls_at_16_24_01() {
    let s = state_at(t(16, 24), 1);
    assert_eq!(s.train, Some(t(16, 34)));
    assert_eq!(s.bus2.as_ref().unwrap().countdown_min, 0);

    let s = state_at(t(16, 24) + 1, 1);
    assert_eq!(s.train, Some(t(17, 4)));
}

#[test]
fn at_15_36_catchable_via_bus_2_only() {
    let s = state_at(t(15, 36), 1);
    assert_eq!(s.train, Some(t(16, 4)));
    assert!(!s.bus1.as_ref().unwrap().catchable);
    assert!(s.bus2.as_ref().unwrap().catchable);
    assert_eq!(s.arrive, Some(t(15, 58)));
    assert_eq!(s.wait_min, Some(6));
}

#[test]
fn last_train_shows_until_18_25() {
    let s = state_at(t(18, 25), 1);
    assert_eq!(s.train, Some(t(18, 34)));
    assert!(s.next_train.is_none());

    let s = state_at(t(18, 25) + 1, 1);
    assert!(matches!(s.mode, Mode::NoTrains));
}

#[test]
fn weekend_mode() {
    for day in [0u16, 6u16] {
        let s = state_at(t(15, 50), day);
        assert!(matches!(s.mode, Mode::Weekend));
    }
}

#[test]
fn before_first_train_shows_first() {
    let s = state_at(t(10, 0), 1);
    assert_eq!(s.train, Some(t(16, 4)));
    assert_eq!(s.bus1.as_ref().unwrap().countdown_min, 351);
    assert_eq!(s.arrive, Some(t(15, 55)));
    assert_eq!(s.wait_min, Some(9));
}

#[test]
fn line_format() {
    let s = state_at(t(15, 50), 1);
    assert_eq!(s.line(), "16:04 · 114 in 1m · 106 in 4m");

    let s = state_at(t(18, 25) + 1, 1);
    assert_eq!(s.line(), "No trains today");

    let s = state_at(t(15, 50), 6);
    assert_eq!(s.line(), "Weekend — no trains");
}

#[test]
fn tooltip_contains_details() {
    let s = state_at(t(15, 50), 1);
    let tip = s.tooltip();
    assert!(tip.contains("Catch the train at 16:04"));
    assert!(tip.contains("Next after: 16:34"));
    assert!(tip.contains("Bus 114 dep 15:51 · leave desk 15:48"));
    assert!(tip.contains("Bus 106 dep 15:54 · leave desk 15:51"));
    assert!(tip.contains("Too late to make the buses"));
}

#[test]
fn urgency_colors_follow_slack() {
    let calm = state_at(t(10, 0), 1);
    assert_eq!(calm.urgency(), 0);

    let warn = state_at(t(15, 57), 1);
    assert_eq!(warn.urgency(), 1);

    let urgent = state_at(t(16, 1), 1);
    assert_eq!(urgent.urgency(), 2);

    let missed = state_at(t(16, 13), 1);
    assert_eq!(missed.urgency(), 0);
}
