package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler.Connection;
import blusunrize.immersiveengineering.api.energy.wires.TileEntityImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.WireType;
import blusunrize.immersiveengineering.api.energy.wires.redstone.IRedstoneConnector;
import blusunrize.immersiveengineering.api.energy.wires.redstone.RedstoneWireNetwork;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntityConnectorRedstone extends TileEntityImmersiveConnectable implements ITickable, IDirectionalTile, IRedstoneOutput, IHammerInteraction, IBlockBounds, IBlockOverlayText, IOBJModelCallback<IBlockState>, IRedstoneConnector
{
	public EnumFacing facing = EnumFacing.DOWN;
	public int ioMode = 0; // 0 - input, 1 -output
	public int redstoneChannel = 0;
	public boolean rsDirty = false;

	protected RedstoneWireNetwork wireNetwork = new RedstoneWireNetwork().add(this);
	private boolean loaded = false;
	// ONLY EVER USE THIS ON THE CLIENT! I don't want to sync the entire network...
	private int outputClient = -1;

	@Override
	public void update()
	{
		if(hasWorld() && !world.isRemote && !loaded)
		{
			loaded = true;
			wireNetwork.removeFromNetwork(null);
		}
		if (hasWorld() && !world.isRemote && rsDirty)
			wireNetwork.updateValues();
	}

	@Override
	public int getStrongRSOutput(IBlockState state, EnumFacing side)
	{
		if(!isRSOutput() || side != this.facing.getOpposite())
			return 0;
		if (world.isRemote)
			return outputClient;
		return wireNetwork != null ? wireNetwork.getPowerOutput(redstoneChannel) : 0;
	}

	@Override
	public int getWeakRSOutput(IBlockState state, EnumFacing side)
	{
		if(!isRSOutput())
			return 0;
		if (world.isRemote)
			return outputClient;
		return wireNetwork != null ? wireNetwork.getPowerOutput(redstoneChannel) : 0;
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, EnumFacing side)
	{
		return true;
	}

	@Override
	public void setNetwork(RedstoneWireNetwork net)
	{
		wireNetwork = net;
	}

	@Override
	public RedstoneWireNetwork getNetwork()
	{
		return wireNetwork;
	}

	@Override
	public void onChange()
	{
		if (!isInvalid() && isRSOutput())
		{
			markDirty();
			markContainingBlockForUpdate(null);
			markBlockForUpdate(pos.offset(facing), null);
		}
	}

	@Override
	public World getConnectorWorld()
	{
		return getWorld();
	}

	public boolean isRSInput()
	{
		return ioMode == 0;
	}

	@Override
	public void updateInput(byte[] signals)
	{
		if (isRSInput())
			signals[redstoneChannel] = (byte) Math.max(getLocalRS(), signals[redstoneChannel]);
		rsDirty = false;
	}
	protected int getLocalRS()
	{
		int val = world.isBlockIndirectlyGettingPowered(pos);
		if (val == 0)
		{
			for (EnumFacing f:EnumFacing.HORIZONTALS)
			{
				IBlockState state = world.getBlockState(pos.offset(f));
				if (state.getBlock()== Blocks.REDSTONE_WIRE&&state.getValue(BlockRedstoneWire.POWER)>val)
					val = state.getValue(BlockRedstoneWire.POWER);
			}
		}
		return val;
	}

	public boolean isRSOutput()
	{
		return ioMode == 1;
	}

	@Override
	public boolean hammerUseSide(EnumFacing side, EntityPlayer player, float hitX, float hitY, float hitZ)
	{
		//Sneaking iterates through colours, normal hammerign toggles in and out
		if(player.isSneaking())
			redstoneChannel = (redstoneChannel + 1) % 16;
		else
			ioMode = ioMode == 0 ? 1 : 0;
		markDirty();
		wireNetwork.updateValues();
		onChange();
		this.markContainingBlockForUpdate(null);
		world.addBlockEvent(getPos(), this.getBlockType(), 254, 0);
		return true;
	}

	@Override
	public boolean canConnectCable(WireType cableType, TargetingInfo target)
	{
		if(cableType != WireType.REDSTONE)
			return false;
		return limitType == null || limitType == cableType;
	}

	@Override
	public void connectCable(WireType cableType, TargetingInfo target, IImmersiveConnectable other)
	{
		super.connectCable(cableType, target, other);
		if(other instanceof IRedstoneConnector)
			if(((IRedstoneConnector) other).getNetwork() != wireNetwork)
				wireNetwork.mergeNetwork(((IRedstoneConnector) other).getNetwork());
	}

	@Override
	public void removeCable(ImmersiveNetHandler.Connection connection)
	{
		super.removeCable(connection);
		wireNetwork.removeFromNetwork(this);
	}

	@Override
	public EnumFacing getFacing()
	{
		return this.facing;
	}

	@Override
	public void setFacing(EnumFacing facing)
	{
		this.facing = facing;
	}

	@Override
	public int getFacingLimitation()
	{
		return 0;
	}

	@Override
	public boolean mirrorFacingOnPlacement(EntityLivingBase placer)
	{
		return true;
	}

	@Override
	public boolean canHammerRotate(EnumFacing side, float hitX, float hitY, float hitZ, EntityLivingBase entity)
	{
		return false;
	}
	@Override
	public boolean canRotate(EnumFacing axis)
	{
		return false;
	}


	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.setInteger("facing", facing.ordinal());
		nbt.setInteger("ioMode", ioMode);
		nbt.setInteger("redstoneChannel", redstoneChannel);
		nbt.setInteger("output", wireNetwork != null ? wireNetwork.getPowerOutput(redstoneChannel) : 0);
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		facing = EnumFacing.getFront(nbt.getInteger("facing"));
		ioMode = nbt.getInteger("ioMode");
		redstoneChannel = nbt.getInteger("redstoneChannel");
		outputClient = nbt.getInteger("output");
	}

	@Override
	public Vec3d getRaytraceOffset(IImmersiveConnectable link)
	{
		EnumFacing side = facing.getOpposite();
		return new Vec3d(.5 + side.getFrontOffsetX() * .0625, .5 + side.getFrontOffsetY() * .0625, .5 + side.getFrontOffsetZ() * .0625);
	}

	@Override
	public Vec3d getConnectionOffset(Connection con)
	{
		EnumFacing side = facing.getOpposite();
		double conRadius = con.cableType.getRenderDiameter() / 2;
		return new Vec3d(.5 - conRadius * side.getFrontOffsetX(), .5 - conRadius * side.getFrontOffsetY(), .5 - conRadius * side.getFrontOffsetZ());
	}

	@SideOnly(Side.CLIENT)
	private AxisAlignedBB renderAABB;

	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		int inc = getRenderRadiusIncrease();
		return new AxisAlignedBB(this.pos.getX() - inc, this.pos.getY() - inc, this.pos.getZ() - inc, this.pos.getX() + inc + 1, this.pos.getY() + inc + 1, this.pos.getZ() + inc + 1);
	}

	int getRenderRadiusIncrease()
	{
		return WireType.REDSTONE.getMaxLength();
	}

	@Override
	public float[] getBlockBounds()
	{
		float length = .625f;
		float wMin = .3125f;
		float wMax = .6875f;
		switch(facing.getOpposite())
		{
			case UP:
				return new float[]{wMin, 0, wMin, wMax, length, wMax};
			case DOWN:
				return new float[]{wMin, 1 - length, wMin, wMax, 1, wMax};
			case SOUTH:
				return new float[]{wMin, wMin, 0, wMax, wMax, length};
			case NORTH:
				return new float[]{wMin, wMin, 1 - length, wMax, wMax, 1};
			case EAST:
				return new float[]{0, wMin, wMin, length, wMax, wMax};
			case WEST:
				return new float[]{1 - length, wMin, wMin, 1, wMax, wMax};
		}
		return new float[]{0, 0, 0, 1, 1, 1};
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldRenderGroup(IBlockState object, String group)
	{
		if("io_out".equals(group))
			return this.ioMode == 1;
		else if("io_in".equals(group))
			return this.ioMode == 0;
		return true;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public int getRenderColour(IBlockState object, String group)
	{
		if("coloured".equals(group))
			return 0xff000000|EnumDyeColor.byMetadata(this.redstoneChannel).getColorValue();
		return 0xffffffff;
	}
	
	@Override
	public String getCacheKey(IBlockState object)
	{
		return redstoneChannel+";"+ioMode;
	}

	@Override
	public String[] getOverlayText(EntityPlayer player, RayTraceResult mop, boolean hammer)
	{
		if(!hammer)
			return null;
		return new String[]{
				I18n.format(Lib.DESC_INFO + "redstoneChannel", I18n.format("item.fireworksCharge." + EnumDyeColor.byMetadata(redstoneChannel).getUnlocalizedName())),
				I18n.format(Lib.DESC_INFO + "blockSide.io." + this.ioMode)
		};
	}

	@Override
	public boolean useNixieFont(EntityPlayer player, RayTraceResult mop)
	{
		return false;
	}

	}