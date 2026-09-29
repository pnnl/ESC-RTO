Clock.Ticket ticket;

double ylast = 0.0;
boolean initialized = false;

public void onStart() throws Exception
{
  if (getEnable().isNull())    getEnable().setValue(true);
  if (getTauSec().isNull())    getTauSec().setValue(5.0);
  if (getPeriodSec().isNull()) getPeriodSec().setValue(1.0);

  getY().setValue(0.0);
  getY().setStatus(BStatus.ok);
  getHeartbeat().setValue(0.0);
  getHeartbeat().setStatus(BStatus.ok);

  updateTimer();
}

public void onExecute() throws Exception
{
  updateTimer();

  if (!getEnable().getStatus().isOk() || !getEnable().getValue())
  {
    getY().setStatus(BStatus.DISABLED);
    return;
  }

  if (!getX().getStatus().isOk() || !getTauSec().getStatus().isOk())
  {
    getY().setStatus(BStatus.NULL);
    return;
  }

  double x   = getX().getValue();
  double tau = Math.max(0.1, getTauSec().getValue());

  double dt = 1.0;
  if (getPeriodSec().getStatus().isOk())
    dt = Math.max(0.2, getPeriodSec().getValue());

  if (!initialized)
  {
    ylast = x;
    initialized = true;
  }

  double a = Math.exp(-dt / tau);
  double y = a * ylast + (1.0 - a) * x;
  ylast = y;

  getY().setValue(y);
  getY().setStatus(BStatus.ok);
}

public void onStop() throws Exception
{
  if (ticket != null)
  {
    ticket.cancel();
    ticket = null;
  }
}

void updateTimer()
{
  if (ticket != null)
    ticket.cancel();

  int sec = 1;
  if (getPeriodSec() != null && getPeriodSec().getStatus().isOk())
    sec = (int)Math.max(1.0, getPeriodSec().getValue());

  ticket = Clock.schedule(
      getComponent(),
      BRelTime.makeSeconds(sec),
      BProgram.execute,
      null);
}