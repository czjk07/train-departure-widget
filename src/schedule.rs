pub const BUFFER_MIN: i64 = 15;
pub const WALK_MIN: i64 = 3;
pub const RIDE_MIN: i64 = 4;
pub const NEED_SECONDS: i64 = (BUFFER_MIN + WALK_MIN) * 60;

pub struct Train {
    pub dep: u32,
    pub bus1: u32,
    pub bus2: u32,
}

const fn hm(h: u32, m: u32) -> u32 {
    h * 3600 + m * 60
}

pub const TRAINS: [Train; 6] = [
    Train { dep: hm(16, 4), bus1: hm(15, 51), bus2: hm(15, 54) },
    Train { dep: hm(16, 34), bus1: hm(16, 21), bus2: hm(16, 24) },
    Train { dep: hm(17, 4), bus1: hm(16, 51), bus2: hm(16, 54) },
    Train { dep: hm(17, 34), bus1: hm(17, 21), bus2: hm(17, 24) },
    Train { dep: hm(18, 4), bus1: hm(17, 51), bus2: hm(17, 54) },
    Train { dep: hm(18, 34), bus1: hm(18, 22), bus2: hm(18, 25) },
];

pub enum Mode {
    Weekend,
    NoTrains,
    Train,
}

pub struct BusCard {
    pub route: &'static str,
    pub departs: u32,
    pub out_of_seat: u32,
    pub catchable: bool,
    pub countdown_min: i64,
    pub slack_min: i64,
}

pub struct State {
    pub mode: Mode,
    pub train: Option<u32>,
    pub next_train: Option<u32>,
    pub bus1: Option<BusCard>,
    pub bus2: Option<BusCard>,
    pub arrive: Option<u32>,
    pub wait_min: Option<i64>,
}

pub fn state_at(now_sec: u32, day_of_week: u16) -> State {
    if day_of_week == 0 || day_of_week == 6 {
        return State {
            mode: Mode::Weekend,
            train: None,
            next_train: None,
            bus1: None,
            bus2: None,
            arrive: None,
            wait_min: None,
        };
    }
    let now = now_sec as i64;
    let index = TRAINS
        .iter()
        .position(|t| t.bus2 as i64 - now >= 0)
        .unwrap_or(TRAINS.len());
    if index >= TRAINS.len() {
        return State {
            mode: Mode::NoTrains,
            train: None,
            next_train: None,
            bus1: None,
            bus2: None,
            arrive: None,
            wait_min: None,
        };
    }
    let target = &TRAINS[index];
    let first_catchable = [target.bus1, target.bus2]
        .iter()
        .copied()
        .find(|b| *b as i64 - now >= NEED_SECONDS);
    let arrive = first_catchable.map(|b| b + (RIDE_MIN * 60) as u32);
    State {
        mode: Mode::Train,
        train: Some(target.dep),
        next_train: TRAINS.get(index + 1).map(|t| t.dep),
        bus1: Some(bus_card("114", target.bus1, now)),
        bus2: Some(bus_card("106", target.bus2, now)),
        arrive,
        wait_min: arrive.map(|a| (target.dep as i64 - a as i64) / 60),
    }
}

fn bus_card(route: &'static str, departs: u32, now: i64) -> BusCard {
    let to_dep = departs as i64 - now;
    BusCard {
        route,
        departs,
        out_of_seat: departs - (WALK_MIN * 60) as u32,
        catchable: to_dep >= NEED_SECONDS,
        countdown_min: to_dep.max(0) / 60,
        slack_min: (to_dep - NEED_SECONDS) / 60,
    }
}

pub fn fmt_hm(s: u32) -> String {
    format!("{:02}:{:02}", s / 3600, s % 3600 / 60)
}

impl State {
    pub fn line(&self) -> String {
        match self.mode {
            Mode::Weekend => "Weekend — no trains".to_string(),
            Mode::NoTrains => "No trains today".to_string(),
            Mode::Train => {
                let train = fmt_hm(self.train.unwrap());
                let b1 = self.bus1.as_ref().unwrap();
                let b2 = self.bus2.as_ref().unwrap();
                format!(
                    "{} · {} in {}m · {} in {}m",
                    train, b1.route, b1.countdown_min, b2.route, b2.countdown_min
                )
            }
        }
    }

    pub fn tooltip(&self) -> String {
        match self.mode {
            Mode::Weekend => "Weekend — no trains on this timetable".to_string(),
            Mode::NoTrains => "No more trains today".to_string(),
            Mode::Train => {
                let mut lines = vec![format!("Catch the train at {}", fmt_hm(self.train.unwrap()))];
                if let Some(next) = self.next_train {
                    lines.push(format!("Next after: {}", fmt_hm(next)));
                } else {
                    lines.push("Last train today".to_string());
                }
                for bus in [&self.bus1, &self.bus2].into_iter().flatten() {
                    lines.push(format!(
                        "Bus {} dep {} · leave desk {}",
                        bus.route,
                        fmt_hm(bus.departs),
                        fmt_hm(bus.out_of_seat)
                    ));
                }
                if let Some(arrive) = self.arrive {
                    lines.push(format!(
                        "Arrive at stop {} · {} min wait",
                        fmt_hm(arrive),
                        self.wait_min.unwrap_or(0)
                    ));
                } else {
                    lines.push("Too late to make the buses".to_string());
                }
                lines.join("\n")
            }
        }
    }

    pub fn urgency(&self) -> u8 {
        match self.mode {
            Mode::Train => {
                let slack = [&self.bus1, &self.bus2]
                    .into_iter()
                    .flatten()
                    .filter(|b| b.catchable)
                    .map(|b| b.slack_min)
                    .min();
                match slack {
                    Some(s) if s < 3 => 2,
                    Some(s) if s < 10 => 1,
                    _ => 0,
                }
            }
            _ => 0,
        }
    }
}
