package nep.timeline.cirno.virtuals

import nep.timeline.cirno.reflect.CakeReflection

class BroadcastRecord(val instance: Any?) {
    fun setDelivery(index: Int, value: Int) {
        (CakeReflection.getObjectField(instance, "delivery") as IntArray)[index] = value
    }

    fun skippedDelivery(index: Int) {
        setDelivery(index, 2)
    }
}
