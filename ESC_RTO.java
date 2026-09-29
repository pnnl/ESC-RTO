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
//Extremum-seeking / relay-gradient optimizer
// Port of the provided MATLAB discrete algorithm
// Persistent state + configurable period
 
Clock.Ticket ticket;
 
// Persistent state
double y      = 0.0;     // output signal from ESC
double e      = -1.0;    // relay output (±1)
double elast  = -1.0;    // previous relay
double ctime  = 0.0;     // hold counter (in number of executions)
double ulast  = 0.0;     // previous u
double g      = 0.0;     // gradient estimate
boolean initialized = false;
  
public void onStart() throws Exception
{
  // Start the periodic timer
  updateTimer();
 
  // Safe defaults
  double ymax = getYmax().getValue();
  double ymin = getYmin().getValue();
  getY().setValue(ymin + (ymax-ymin)/2.0);
  getY().setStatus(BStatus.ok);
 
  if (getEnable().isNull()) getEnable().setValue(true);
  if (getYmax().isNull())   getYmax().setValue(1.0);
  if (getYmin().isNull())   getYmin().setValue(0.0);
  if (getTC().isNull())     getTC().setValue(60.0);
  if (getPeriodSec().isNull()) getPeriodSec().setValue(1.0);
}
 
public void onExecute() throws Exception
{
  // Always reschedule so the block keeps running at the requested period
  updateTimer();
 
  // ----- Read & validate inputs -----
  if (!getEnable().getStatus().isOk() || !getEnable().getValue())
  {
    // Disabled → hold last output, mark status
    getY().setStatus(BStatus.DISABLED);
    return;
  }
 
  boolean uOk    = getU().getStatus().isOk();
  boolean ymaxOk = getYmax().getStatus().isOk();
  boolean yminOk = getYmin().getStatus().isOk();
  boolean tcOk   = getTC().getStatus().isOk();
 
  if (!uOk || !ymaxOk || !yminOk || !tcOk)
  {
    getY().setStatus(BStatus.NULL);
    return;
  }
 
  double u    = getU().getValue();
  double ymax = getYmax().getValue();
  double ymin = getYmin().getValue();
  double TC   = Math.max(1.0, getTC().getValue());   // prevent divide-by-zero / negative
  double dT   = getPeriodSec().getValue();

  // Recompute gain every cycle (robust if parameters change at runtime)
  double K0 = dT*(ymax - ymin) / (5.0 * TC);
  double clim = TC;   // hold time in number of executions
 
  // ----- Initialization (first valid sample) -----
  if (!initialized)
  {
    y      = ymin + (ymax-ymin)/2.0;
    e      = -1.0;
    elast  = e;
    ctime  = 0.0;
    ulast  = u;
    initialized = true;
  }
 
  // ----- Core algorithm -----
  ctime = ctime + dT;
 
  // change in input
  g = (u - ulast);
 
  // Direction change condition
  if (g > 0.0 && ctime >= clim)
  {
    e = -e;
    ctime = 0.0;
  }
 
  // Integrator step
  y = y + e * K0;
 
  // Apply limits + anti-saturation flip
  if (y >= ymax || y <= ymin)
  {
    y = Math.min(Math.max(y, ymin), ymax);
 
    if (e == elast)
    {
      e = -e;
      ctime = 0.0;
    }
  }
 
  // Update persistent state
  ulast = u;
  elast = e;
 
  // ----- Write outputs -----
  getY().setValue(y);
  getY().setStatus(BStatus.ok);
 
  // Optional debug outputs (safe even if slots were not created)
  try { getGOut().setValue(g);     getGOut().setStatus(BStatus.ok); } catch (Exception ignore) {}
  try { getEOut().setValue(e);     getEOut().setStatus(BStatus.ok); } catch (Exception ignore) {}
  try { getCtimeOut().setValue(ctime); getCtimeOut().setStatus(BStatus.ok); } catch (Exception ignore) {}
}
 
public void onStop() throws Exception
{
  if (ticket != null)
  {
    ticket.cancel();
    ticket = null;
  }
}
 
// ------------------------------------------------------------------
// Timer helper – period taken from the periodSec slot
// ------------------------------------------------------------------
void updateTimer()
{
  if (ticket != null)
  {
    ticket.cancel();
  }
 
  double sec = 1.0;
  if (getPeriodSec().getStatus().isOk())
  {
    sec = Math.max(0.2, getPeriodSec().getValue());   // minimum 200 ms for safety
  }
 
  ticket = Clock.schedule(getComponent(),
                          BRelTime.makeSeconds((int)sec),
                          BProgram.execute,
                          null);
}
 