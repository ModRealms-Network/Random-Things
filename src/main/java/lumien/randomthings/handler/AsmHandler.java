package lumien.randomthings.handler;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import lumien.randomthings.asm.MCPNames;
import lumien.randomthings.block.ModBlocks;
import lumien.randomthings.enchantment.ModEnchantments;
import lumien.randomthings.handler.redstonesignal.RedstoneSignalHandler;
import lumien.randomthings.item.ItemRedstoneTool;
import lumien.randomthings.item.ItemSpectreKey;
import lumien.randomthings.item.ItemSpectreSword;
import lumien.randomthings.item.ModItems;
import lumien.randomthings.lib.ILuminousBlock;
import lumien.randomthings.lib.ISuperLubricent;
import lumien.randomthings.tileentity.TileEntityLightRedirector;
import lumien.randomthings.tileentity.TileEntityRainShield;
import lumien.randomthings.tileentity.redstoneinterface.TileEntityRedstoneInterface;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent.Open;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

public class AsmHandler
{
	static Random rng = new Random();

	static Field fluidRenderer;
	static
	{
		if (FMLCommonHandler.instance().getSide().isClient())
		{
			getFields();
		}
	}

	public static void updateColor(float[] normal, float[] color, float x, float y, float z, float tint, int multiplier)
	{
		if (tint != -1)
		{
			color[0] *= (float) (multiplier >> 0x10 & 0xFF) / 0xFF;
			color[1] *= (float) (multiplier >> 0x8 & 0xFF) / 0xFF;
			color[2] *= (float) (multiplier & 0xFF) / 0xFF;
		}
	}

	// Called when a tree tries to set the block below it to dirt, returning
	// true prevents that from happening
	public static boolean protectGround(Block b)
	{
		return b == ModBlocks.fertilizedDirt || b == ModBlocks.fertilizedDirtTilled;
	}

	static boolean catchingDrops;
	static List<ItemStack> catchedDrops = new ArrayList<>();
	static PlayerInteractionManager interactionManager;

	public static void preHarvest(PlayerInteractionManager manager)
	{
		ItemStack tool = manager.player.getHeldItemMainhand();

		if (tool != null && EnchantmentHelper.getEnchantmentLevel(ModEnchantments.magnetic, tool) > 0)
		{
			interactionManager = manager;
			catchingDrops = true;
		}
	}

	public static void postHarvest()
	{
		if (catchingDrops)
		{
			catchingDrops = false;

			for (ItemStack is : catchedDrops)
			{
				ItemHandlerHelper.giveItemToPlayer(interactionManager.player, is.copy());
			}

			interactionManager = null;

			catchedDrops.clear();
		}
	}

	public static void itemJoin(EntityJoinWorldEvent event)
	{
		if (catchingDrops && !event.isCanceled())
		{
			catchedDrops.add(((EntityItem) event.getEntity()).getEntityItem());
			event.setCanceled(true);
		}
	}

	public static boolean shouldPlayerDrop(InventoryPlayer inventory, int slot, ItemStack item)
	{
		return !(item.hasTagCompound() && item.getTagCompound().hasKey("spectreAnchor"));
	}

	@SideOnly(Side.CLIENT)
	private static void getFields()
	{
		try
		{
			fluidRenderer = BlockRendererDispatcher.class.getDeclaredField(MCPNames.field("field_175025_e"));
			fluidRenderer.setAccessible(true);
		}
		catch (NoSuchFieldException e)
		{
			e.printStackTrace();
		}
		catch (SecurityException e)
		{
			e.printStackTrace();
		}
	}

	@SideOnly(Side.CLIENT)
	public static int getColorFromItemStack(ItemStack is, int originalColor)
	{
		if (!is.isEmpty())
		{
			NBTTagCompound compound;
			if ((compound = is.getTagCompound()) != null)
			{
				if (compound.hasKey("rtDye"))
				{
					return compound.getInteger("rtDye") | -16777216;
				}
			}
		}
		return originalColor;
	}

	public static boolean shouldRain(World worldObj, BlockPos pos)
	{
		return TileEntityRainShield.shouldRain(worldObj, pos.add(0, -pos.getY(), 0));
	}

	public static boolean shouldRenderPotionParticles(EntityLivingBase entity)
	{
		if (entity != null && entity instanceof EntityPlayer)
		{
			ItemStack helmet = ((EntityPlayer) entity).getItemStackFromSlot(EntityEquipmentSlot.HEAD);
			if (helmet != null && helmet.getItem() == ModItems.magicHood)
			{
				return false;
			}
		}
		return true;
	}

	// False returns false, true runs vanilla behaviour
	@SideOnly(Side.CLIENT)
	public static boolean canRenderName(EntityLivingBase e)
	{
		if (e != null && e instanceof EntityPlayer)
		{
			ItemStack helmet = ((EntityPlayer) e).getItemStackFromSlot(EntityEquipmentSlot.HEAD);
			if (helmet != null && helmet.getItem() == ModItems.magicHood)
			{
				return false;
			}
		}
		return true;
	}

	@SideOnly(Side.CLIENT)
	public static int renderBlock(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos, IBlockAccess blockAccess, VertexBuffer worldRendererIn)
	{
		synchronized (TileEntityLightRedirector.redirectorSet)
		{
			if (!TileEntityLightRedirector.redirectorSet.isEmpty())
			{
				blockAccess = Minecraft.getMinecraft().world;

				BlockPos changedPos = getSwitchedPosition(blockAccess, pos);

				posSet.clear();

				if (!changedPos.equals(pos))
				{
					state = blockAccess.getBlockState(changedPos);

					try
					{
						EnumBlockRenderType enumblockrendertype = state.getRenderType();

						if (enumblockrendertype == EnumBlockRenderType.INVISIBLE)
						{

						}
						else
						{
							if (blockAccess.getWorldType() != WorldType.DEBUG_WORLD)
							{
								try
								{
									state = state.getActualState(blockAccess, changedPos);
								}
								catch (Exception var8)
								{
									;
								}
							}

							switch (enumblockrendertype)
							{
								case MODEL:
									IBakedModel model = dispatcher.getModelForState(state);
									state = state.getBlock().getExtendedState(state, blockAccess, changedPos);
									return dispatcher.getBlockModelRenderer().renderModel(blockAccess, model, state, pos, worldRendererIn, true) ? 1 : 0;
								case ENTITYBLOCK_ANIMATED:
									return 0;
								case LIQUID:
									return 2;
								default:
									return 0;
							}
						}
					}
					catch (Throwable throwable)
					{
						CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Tesselating block in world");
						CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being tesselated");
						CrashReportCategory.addBlockInfo(crashreportcategory, pos, state.getBlock(), state.getBlock().getMetaFromState(state));
						throw new ReportedException(crashreport);
					}

					return 0;
				}
			}

			return 2;
		}
	}

	static HashSet<BlockPos> posSet = new HashSet<>();

	public static BlockPos getSwitchedPosition(IBlockAccess access, BlockPos pos)
	{
		if (pos != null && access != null)
		{
			Iterator<TileEntityLightRedirector> iterator = TileEntityLightRedirector.redirectorSet.iterator();
			while (iterator.hasNext())
			{
				TileEntityLightRedirector redirector = iterator.next();
				if (redirector.isInvalid())
				{
					iterator.remove();
				}
				else
				{
					if (redirector.established && !posSet.contains(redirector.getPos()))
					{
						posSet.add(redirector.getPos());

						if (redirector.targets.isEmpty())
						{
							for (EnumFacing facing : EnumFacing.values())
							{
								redirector.targets.put(redirector.getPos().offset(facing), redirector.getPos().offset(facing.getOpposite()));
							}
						}

						if (redirector.targets.containsKey(pos))
						{
							BlockPos switched = redirector.targets.get(pos);

							if (!access.isAirBlock(switched))
							{
								return getSwitchedPosition(access, switched);
							}
						}
					}
				}
			}
		}

		return pos;
	}

	public static int getRedstonePower(World worldObj, BlockPos pos, EnumFacing facing)
	{
		return Math.max(TileEntityRedstoneInterface.getRedstonePower(worldObj, pos, facing), worldObj.isRemote ? 0 : RedstoneSignalHandler.getHandler().getStrongPower(worldObj, pos, facing));
	}

	public static int getStrongPower(World worldObj, BlockPos pos, EnumFacing facing)
	{
		return Math.max(TileEntityRedstoneInterface.getStrongPower(worldObj, pos, facing), worldObj.isRemote ? 0 : RedstoneSignalHandler.getHandler().getStrongPower(worldObj, pos, facing));
	}

	// Returns whether to cancel normal behaviour
	public static boolean addCollisionBoxesToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB mask, List list, Entity collidingEntity)
	{
		if (collidingEntity != null && collidingEntity instanceof EntityPlayer)
		{
			EntityPlayer player = (EntityPlayer) collidingEntity;

			if (state.getBlock() instanceof BlockLiquid && collidingEntity.posY > pos.getY() + 0.9 && !(worldIn.getBlockState(pos.up()).getBlock().getMaterial(worldIn.getBlockState(pos.up())) == Material.LAVA || worldIn.getBlockState(pos.up()).getBlock().getMaterial(worldIn.getBlockState(pos.up())) == Material.WATER))
			{
				if (!player.isSneaking())
				{
					ItemStack boots = player.inventory.armorInventory.get(0);
					if (boots != null && ((((boots.getItem() == ModItems.waterWalkingBoots || boots.getItem() == ModItems.obsidianWaterWalkingBoots) || boots.getItem() == ModItems.lavaWader) && state.getBlock().getMaterial(state) == Material.WATER) || (boots.getItem() == ModItems.lavaWader && state.getBlock().getMaterial(state) == Material.LAVA)))
					{
						AxisAlignedBB bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), (double) pos.getX() + 1, (double) pos.getY() + 1, (double) pos.getZ() + 1);
						if (mask.intersectsWith(bb))
						{
							list.add(bb);
						}
						return true;
					}
				}
			}
		}

		return false;
	}

	static float enchantmentLightMapX;
	static float enchantmentLightMapY;

	public static void preEnchantment()
	{
		if (currentlyRendering != null && currentlyRendering.hasTagCompound() && currentlyRendering.getTagCompound().hasKey("luminousEnchantment"))
		{
			enchantmentLightMapX = OpenGlHelper.lastBrightnessX;
			enchantmentLightMapY = OpenGlHelper.lastBrightnessY;

			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		}
	}

	public static void postEnchantment()
	{
		if (currentlyRendering != null && currentlyRendering.hasTagCompound() && currentlyRendering.getTagCompound().hasKey("luminousEnchantment"))
		{
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, enchantmentLightMapX, enchantmentLightMapY);
		}
	}

	public static ItemStack currentlyRendering = null;

	public static int enchantmentColorHook()
	{
		if (currentlyRendering != null)
		{
			if (currentlyRendering.getItem() instanceof ItemRedstoneTool)
			{
				return Color.RED.darker().getRGB() | -16777216;
			}

			if (currentlyRendering.getItem() instanceof ItemSpectreKey)
			{
				return Color.CYAN.darker().getRGB() | -16777216;
			}

			NBTTagCompound compound;
			if ((compound = currentlyRendering.getTagCompound()) != null)
			{
				if (compound.hasKey("enchantmentColor"))
				{
					return compound.getInteger("enchantmentColor") | -16777216;
				}
			}

			if (currentlyRendering.getItem() instanceof ItemSpectreSword)
			{
				return Color.WHITE.darker().darker().getRGB() | -16777216;
			}
		}

		return -8372020;
	}

	public static void armorColorHook(ItemStack stack)
	{
		NBTTagCompound compound;
		if ((compound = stack.getTagCompound()) != null)
		{
			if (compound.hasKey("rtDye"))
			{
				Color c = new Color(compound.getInteger("rtDye"));

				GlStateManager.color(1F / 255F * c.getRed(), 1F / 255F * c.getGreen(), 1F / 255F * c.getBlue());
			}
		}
	}

	public static void armorEnchantmentHook()
	{
		int color = enchantmentColorHook();

		if (color != -8372020)
		{
			Color c = new Color(color);
			c = c.darker();
			GlStateManager.color(1F / 255F * c.getRed(), 1F / 255F * c.getGreen(), 1F / 255F * c.getBlue());
		}
	}

	static Block changed;

	public static void preSlipFix(Block b)
	{
		if (b instanceof ISuperLubricent)
		{
			b.slipperiness = 1F / 0.91F;
			changed = b;
		}
	}

	public static void postSlipFix()
	{
		if (changed != null)
		{
			changed.slipperiness = 1F / 0.98F;
			changed = null;
		}
	}
}
