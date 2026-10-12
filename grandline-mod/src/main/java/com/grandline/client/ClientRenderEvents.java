package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.client.anim.Animator;
import com.grandline.client.render.RaceVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Whole-body animation transforms, race proportions lift, first-person animation and camera shake. */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ClientRenderEvents {
    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        float lift = (RaceVisuals.legScale(RaceVisuals.of(le).race()) - 1f) * 0.75f * le.getScale();
        if (lift != 0) ps.translate(0, lift, 0);
        float[] r = Animator.root(le, e.getPartialTick());
        if (r != null) {
            float yaw = net.minecraft.util.Mth.rotLerp(e.getPartialTick(), le.yBodyRotO, le.yBodyRot);
            ps.translate(0, le.getBbHeight() / 2, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(r[0], r[1], r[2]);
            ps.mulPose(Axis.XP.rotationDegrees(r[3]));
            ps.mulPose(Axis.YP.rotationDegrees(r[4]));
            ps.mulPose(Axis.ZP.rotationDegrees(r[5]));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            ps.translate(0, -le.getBbHeight() / 2, 0);
        }
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> e) {
        e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float[] f = Animator.firstPerson(p, e.getPartialTick());
        if (f == null) return;
        PoseStack ps = e.getPoseStack();
        ps.translate(f[0], f[1], f[2]);
        ps.mulPose(Axis.XP.rotationDegrees(f[3]));
        ps.mulPose(Axis.YP.rotationDegrees(f[4]));
        ps.mulPose(Axis.ZP.rotationDegrees(f[5]));
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (ClientFx.shakeTicks > 0) {
            ClientFx.shakeTicks--;
            float amt = Math.min(3f, ClientFx.shakeTicks * 0.3f);
            e.setYaw(e.getYaw() + (float) (Math.random() - 0.5) * amt);
            e.setPitch(e.getPitch() + (float) (Math.random() - 0.5) * amt);
        }
    }

    private ClientRenderEvents() {}
}
