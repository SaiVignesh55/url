import React, { useState } from 'react'
import { useStoreContext } from '../../contextApi/ContextApi';
import { useForm } from 'react-hook-form';
import TextField from '../TextField';
import { Tooltip } from '@mui/material';
import { RxCross2 } from 'react-icons/rx';
import api from '../../api/api';
import toast from 'react-hot-toast';

const CUSTOM_ALIAS_REGEX = /^[a-zA-Z0-9-]+$/;
const CUSTOM_ALIAS_MIN_LENGTH = 3;
const CUSTOM_ALIAS_MAX_LENGTH = 30;

const CreateNewShorten = ({ setOpen, refetch }) => {
    const { token } = useStoreContext();
    const [loading, setLoading] = useState(false);
    const backendBaseUrl = import.meta.env.VITE_BACKEND_URL || "http://localhost:9000";

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isValid },
  } = useForm({
    defaultValues: {
      longUrl: "",
      customAlias: "",
    },
    mode: "onChange",
  });

  const createShortUrlHandler = async (data) => {
    setLoading(true);
    try {
        const payload = {
          longUrl: data.longUrl,
          ...(data.customAlias?.trim() ? { customAlias: data.customAlias.trim() } : {}),
        };

        const { data: res } = await api.post("/api/urls/shorten", payload, {
            headers: {
              "Content-Type": "application/json",
              Accept: "application/json",
              Authorization: "Bearer " + token,
            },
          });

          const shortenUrl = `${backendBaseUrl}/r/${res.shortUrl}`;
          navigator.clipboard.writeText(shortenUrl).then(() => {
            toast.success("Short URL Copied to Clipboard", {
                position: "bottom-center",
                className: "mb-5",
                duration: 3000,
            });
          });

          await refetch();
          reset();
          setOpen(false);
    } catch (error) {
        const status = error?.response?.status;
        const message = error?.response?.data?.message;

        if (status === 409) {
          toast.error("Alias already taken");
        } else if (status === 400 && message) {
          toast.error(message);
        } else {
          toast.error("Create ShortURL Failed");
        }
    } finally {
        setLoading(false);
    }
  };


  return (
    <div className=" flex justify-center items-center bg-white rounded-md">
    <form
        onSubmit={handleSubmit(createShortUrlHandler)}
        className="sm:w-[450px] w-[360px] relative  shadow-custom pt-8 pb-5 sm:px-8 px-4 rounded-lg"
      >

        <h1 className="font-montserrat sm:mt-0 mt-3 text-center  font-bold sm:text-2xl text-[22px] text-slate-800 ">
                Create New Shorten Url
        </h1>

        <hr className="mt-2 sm:mb-5 mb-3 text-slate-950" />

        <div>
          <TextField
            label="Enter URL"
            required
            id="longUrl"
            placeholder="https://example.com"
            type="url"
            message="Url is required"
            register={register}
            errors={errors}
          />

          <TextField
            label="Custom Alias (optional)"
            id="customAlias"
            placeholder="my-link"
            type="text"
            register={register}
            errors={errors}
            validate={(value) => {
              const alias = value?.trim();
              if (!alias) {
                return true;
              }
              if (alias.length < CUSTOM_ALIAS_MIN_LENGTH || alias.length > CUSTOM_ALIAS_MAX_LENGTH) {
                return "Alias length must be between 3 and 30 characters";
              }
              if (!CUSTOM_ALIAS_REGEX.test(alias)) {
                return "Alias can only contain letters, numbers, and hyphens";
              }
              return true;
            }}
          />
        </div>

        <button
          className="bg-customRed font-semibold text-white w-32  bg-custom-gradient  py-2  transition-colors  rounded-md my-3"
          type="submit"
          disabled={loading || !isValid}
        >
          {loading ? "Loading..." : "Create"}
        </button>

        {!loading && (
          <Tooltip title="Close">
            <button
              disabled={loading}
              onClick={() => setOpen(false)}
              className=" absolute right-2 top-2  "
            >
              <RxCross2 className="text-slate-800   text-3xl" />
            </button>
          </Tooltip>
        )}

      </form>
    </div>
  )
}

export default CreateNewShorten