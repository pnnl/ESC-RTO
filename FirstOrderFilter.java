/*DISCLAIMER
This material was prepared as an account of work sponsored by an agency of the
United States Government. Neither the United States Government nor the United
States Department of Energy, nor Battelle, nor any of their employees, nor any
jurisdiction or organization that has cooperated in the development of these
materials, makes any warranty, express or implied, or assumes any legal
liability or responsibility for the accuracy, completeness, or usefulness or
any information, apparatus, product, software, or process disclosed, or
represents that its use would not infringe privately owned rights.
Reference herein to any specific commercial product, process, or service by
trade name, trademark, manufacturer, or otherwise does not necessarily
constitute or imply its endorsement, recommendation, or favoring by the United
States Government or any agency thereof, or Battelle Memorial Institute. The
views and opinions of authors expressed herein do not necessarily state or
reflect those of the United States Government or any agency thereof.
PACIFIC NORTHWEST NATIONAL LABORATORY
operated by
BATTELLE
for the
UNITED STATES DEPARTMENT OF ENERGY
under Contract DE-AC05-76RL01830
_____________________________________________________________________________________
LICENSE
Copyright Battelle Memorial Institute 2026
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
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